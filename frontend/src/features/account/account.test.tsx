import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../../App'
import { t } from '../../i18n/t'
import { axe } from '../../test/axe'

const fetchMock = vi.fn()
type Reply = [status: number, body?: unknown]
let replies: Record<string, Reply> = {}
let loggedIn = true

beforeEach(() => {
  loggedIn = true
  replies = {
    'GET /auth/me': [
      200,
      { id: 'u1', username: 'anna', role: 'USER', mustChangePassword: false },
    ],
  }
  fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
    const key = `${init.method} ${url.replace('/api/v1', '')}`
    const r =
      key === 'GET /auth/me' && !loggedIn
        ? ([401, { detail: 'no' }] as Reply)
        : (replies[key] ?? [404, { detail: key }])
    const [status, body] = r
    return new Response(body === undefined ? null : JSON.stringify(body), {
      status,
    })
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  fetchMock.mockReset()
})

function renderApp(path = '/account') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const calls = (method: string, url: string) =>
  fetchMock.mock.calls.filter(
    (c) => c[1].method === method && c[0] === `/api/v1${url}`,
  )

describe('account page', () => {
  it('changesPasswordAndGoesToLogin', async () => {
    replies['POST /auth/password'] = [204]
    renderApp()
    await userEvent.type(
      await screen.findByLabelText('Password attuale'),
      'vecchia-password-1',
    )
    await userEvent.type(
      screen.getByLabelText('Nuova password'),
      'nuova-password-2',
    )
    loggedIn = false
    await userEvent.click(
      screen.getByRole('button', { name: 'Cambia password' }),
    )
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Accedi' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Password cambiata')
  })

  it('exportDownloadsJson', async () => {
    const data = { username: 'anna', role: 'USER', collection: [] }
    replies['GET /account/export'] = [200, data]
    let blob: Blob | undefined
    URL.createObjectURL = vi.fn((b: Blob) => {
      blob = b
      return 'blob:export'
    })
    URL.revokeObjectURL = vi.fn()
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => {})
    renderApp()
    await userEvent.click(
      await screen.findByRole('button', { name: 'Esporta i miei dati' }),
    )
    await vi.waitFor(() => expect(click).toHaveBeenCalledTimes(1))
    const link = click.mock.contexts[0] as HTMLAnchorElement
    expect(link.download).toBe('mangashelf-export.json')
    expect(link.href).toBe('blob:export')
    expect(blob!.type).toBe('application/json')
    expect(await blob!.text()).toBe(JSON.stringify(data, null, 2))
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:export')
  })

  it('exportFailureShowsError', async () => {
    replies['GET /account/export'] = [500, { detail: 'Esportazione fallita' }]
    renderApp()
    await userEvent.click(
      await screen.findByRole('button', { name: 'Esporta i miei dati' }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Esportazione fallita',
    )
  })

  it('deleteAccountRequiresPasswordAndLogsOut', async () => {
    replies['DELETE /account'] = [204]
    renderApp()
    await userEvent.type(
      await screen.findByLabelText('Password per confermare'),
      'la-mia-password-1',
    )
    await userEvent.click(
      screen.getByRole('button', { name: 'Elimina account' }),
    )
    expect(calls('DELETE', '/account')).toHaveLength(0)
    loggedIn = false
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Accedi' }),
    ).toBeInTheDocument()
    expect(JSON.parse(calls('DELETE', '/account')[0][1].body)).toEqual({
      password: 'la-mia-password-1',
    })
  })

  it('wrongPasswordShowsFieldError', async () => {
    replies['DELETE /account'] = [
      400,
      {
        detail: 'Dati non validi',
        errors: [{ field: 'password', message: 'Password errata' }],
      },
    ]
    renderApp()
    const input = await screen.findByLabelText('Password per confermare')
    await userEvent.type(input, 'sbagliata-sbagliata')
    await userEvent.click(
      screen.getByRole('button', { name: 'Elimina account' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    const msg = await screen.findByText('Password errata')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-describedby', msg.id)
  })

  it('lastAdminConflictShown', async () => {
    replies['DELETE /account'] = [409, { detail: 'Sei l’ultimo admin' }]
    renderApp()
    await userEvent.type(
      await screen.findByLabelText('Password per confermare'),
      'la-mia-password-1',
    )
    await userEvent.click(
      screen.getByRole('button', { name: 'Elimina account' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Sei l’ultimo admin')
    expect(alert).toHaveClass('error')
  })

  it('hasNoAccessibilityViolations', async () => {
    const { container } = renderApp()
    await screen.findByLabelText('Password per confermare')
    expect((await axe(container)).violations).toEqual([])
  })
})

describe('privacy page', () => {
  it('isPublicAndStatesTheFacts', async () => {
    loggedIn = false
    const { container } = renderApp('/privacy')
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Privacy' }),
    ).toBeInTheDocument()
    for (const k of [
      'privacy.controller',
      'privacy.processors.hetzner',
      'privacy.processors.infomaniak',
      'privacy.processors.github',
      'privacy.cookies.body',
      'privacy.rights.access',
      'privacy.rights.erasure',
    ] as const)
      expect(screen.getByText(t(k))).toBeInTheDocument()
    expect(t('privacy.controller')).toMatch(/^.*<.+>.*$/)
    expect((await axe(container)).violations).toEqual([])
  })
})
