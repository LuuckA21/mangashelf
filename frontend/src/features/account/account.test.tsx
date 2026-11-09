import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { t } from '../../i18n/t'
import { axe } from '../../test/axe'
import { renderApp } from '../../test/renderApp'

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
    'GET /account/purchase-settings': [
      200,
      { chfRate: 1.2, discountPercent: 10 },
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

const calls = (method: string, url: string) =>
  fetchMock.mock.calls.filter(
    (c) => c[1].method === method && c[0] === `/api/v1${url}`,
  )

describe('account page', () => {
  it('changesPasswordAndGoesToLogin', async () => {
    replies['POST /auth/password'] = [204]
    renderApp('/account')
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
    renderApp('/account')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Esporta i miei dati' }),
    )
    await vi.waitFor(() => expect(click).toHaveBeenCalledTimes(1))
    const link = click.mock.contexts[0] as HTMLAnchorElement
    expect(link.download).toBe('mangashelf-export.json')
    expect(link.href).toBe('blob:export')
    expect(blob!.type).toBe('application/json')
    expect(await blob!.text()).toBe(JSON.stringify(data, null, 2))
    await vi.waitFor(() =>
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:export'),
    )
  })

  it('exportFailureShowsError', async () => {
    replies['GET /account/export'] = [500, { detail: 'Esportazione fallita' }]
    renderApp('/account')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Esporta i miei dati' }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Esportazione fallita',
    )
  })

  it('deleteAccountRequiresPasswordAndLogsOut', async () => {
    replies['DELETE /account'] = [204]
    renderApp('/account')
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
    renderApp('/account')
    const input = await screen.findByLabelText('Password per confermare')
    await userEvent.type(input, 'sbagliata-sbagliata')
    await userEvent.click(
      screen.getByRole('button', { name: 'Elimina account' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    const msg = await screen.findByText('Password errata')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-describedby', msg.id)
    expect(input).toHaveFocus()
  })

  it('unmatchedDeleteErrorShownInAlert', async () => {
    replies['DELETE /account'] = [
      400,
      { detail: 'x', errors: [{ field: 'other', message: 'Campo strano' }] },
    ]
    renderApp('/account')
    await userEvent.type(
      await screen.findByLabelText('Password per confermare'),
      'la-mia-password-1',
    )
    await userEvent.click(
      screen.getByRole('button', { name: 'Elimina account' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Campo strano')
  })

  it('lastAdminConflictShown', async () => {
    replies['DELETE /account'] = [409, { detail: 'Sei l’ultimo admin' }]
    renderApp('/account')
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
    // still logged in: no navigation, the page and the session are intact
    expect(
      screen.getByRole('heading', { level: 1, name: 'Account' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Esci' })).toBeInTheDocument()
  })

  it('hasNoAccessibilityViolations', async () => {
    const { container } = renderApp('/account')
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
