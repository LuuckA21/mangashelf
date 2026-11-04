import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../../App'
import { axe } from '../../test/axe'

const fetchMock = vi.fn()

type Reply = [status: number, body?: unknown]
let replies: Record<string, Reply> = {}

beforeEach(() => {
  replies = {
    'GET /auth/me': [401, { status: 401, detail: 'no' }],
    'GET /collection?page=0&size=20': [
      200,
      {
        content: [],
        page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
      },
    ],
  }
  fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
    const key = `${init.method} ${url.replace('/api/v1', '')}`
    const [status, body] = replies[key] ?? [404, { detail: key }]
    return new Response(body === undefined ? null : JSON.stringify(body), {
      status,
    })
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchMock.mockReset()
})

function renderApp(path: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const view = render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return Object.assign(client, { container: view.container })
}

const me = (extra = {}) => ({
  id: '1',
  username: 'anna',
  role: 'USER',
  mustChangePassword: false,
  ...extra,
})

describe('auth', () => {
  it('redirectsAnonymousUserToLogin', async () => {
    renderApp('/catalog')
    expect(
      await screen.findByRole('heading', { name: 'Accedi' }),
    ).toBeInTheDocument()
  })

  it('loginShowsInvalidCredentialsMessage', async () => {
    replies['POST /auth/login'] = [
      401,
      { status: 401, detail: 'Credenziali non valide' },
    ]
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('Nome utente'), 'anna')
    await userEvent.type(screen.getByLabelText('Password'), 'sbagliata')
    await userEvent.click(screen.getByRole('button', { name: 'Accedi' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Credenziali non valide',
    )
  })

  it('loginGoesToRequestedPage', async () => {
    replies['POST /auth/login'] = [200, me()]
    renderApp('/catalog')
    await userEvent.type(await screen.findByLabelText('Nome utente'), 'anna')
    await userEvent.type(screen.getByLabelText('Password'), 'password-giusta')
    await userEvent.click(screen.getByRole('button', { name: 'Accedi' }))
    expect(
      await screen.findByRole('heading', { name: 'Catalogo' }),
    ).toBeInTheDocument()
  })

  it('temporaryPasswordGoesToChangePassword', async () => {
    replies['POST /auth/login'] = [200, me({ mustChangePassword: true })]
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('Nome utente'), 'anna')
    await userEvent.type(screen.getByLabelText('Password'), 'temporanea-123')
    await userEvent.click(screen.getByRole('button', { name: 'Accedi' }))
    expect(
      await screen.findByRole('heading', { name: 'Cambia la password' }),
    ).toBeInTheDocument()
  })

  it('changePasswordShowsFieldError', async () => {
    replies['GET /auth/me'] = [200, me({ mustChangePassword: true })]
    replies['POST /auth/password'] = [
      400,
      {
        status: 400,
        detail: 'Dati non validi',
        errors: [{ field: 'newPassword', message: 'Troppo corta' }],
      },
    ]
    renderApp('/change-password')
    await userEvent.type(
      await screen.findByLabelText('Password attuale'),
      'temporanea-123',
    )
    await userEvent.type(screen.getByLabelText('Nuova password'), 'corta')
    await userEvent.click(
      screen.getByRole('button', { name: 'Cambia password' }),
    )
    const input = await screen.findByLabelText('Nuova password')
    await waitFor(() => expect(input).toHaveAttribute('aria-invalid', 'true'))
    expect(input).toHaveAccessibleDescription('Troppo corta')
    await waitFor(() => expect(input).toHaveFocus())
  })

  it('changePasswordSuccessGoesToLoginWithMessage', async () => {
    replies['GET /auth/me'] = [200, me({ mustChangePassword: true })]
    replies['POST /auth/password'] = [204]
    renderApp('/change-password')
    await userEvent.type(
      await screen.findByLabelText('Password attuale'),
      'temporanea-123',
    )
    await userEvent.type(
      screen.getByLabelText('Nuova password'),
      'nuova-password-1',
    )
    await userEvent.click(
      screen.getByRole('button', { name: 'Cambia password' }),
    )
    expect(
      await screen.findByRole('heading', { name: 'Accedi' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Password cambiata')
  })

  it('nonAdminSeesNotFoundOnAdminRoute', async () => {
    replies['GET /auth/me'] = [200, me()]
    renderApp('/admin/users')
    expect(
      await screen.findByRole('heading', { name: 'Pagina non trovata' }),
    ).toBeInTheDocument()
  })

  it('privacyIsReachableWithoutLogin', async () => {
    renderApp('/privacy')
    expect(
      await screen.findByRole('heading', { name: 'Privacy' }),
    ).toBeInTheDocument()
  })

  it('logoutClearsUserAndGoesToLogin', async () => {
    replies['GET /auth/me'] = [200, me()]
    replies['POST /auth/logout'] = [204]
    const client = renderApp('/')
    const logout = await screen.findByRole('button', { name: 'Esci' })
    client.setQueryData(['collection'], ['previous user data'])
    await userEvent.click(logout)
    expect(
      await screen.findByRole('heading', { name: 'Accedi' }),
    ).toBeInTheDocument()
    expect(client.getQueryData(['collection'])).toBeUndefined()
  })

  it('logoutFailureShowsErrorAndStays', async () => {
    replies['GET /auth/me'] = [200, me()]
    replies['POST /auth/logout'] = [500, { detail: 'boom' }]
    renderApp('/')
    await userEvent.click(await screen.findByRole('button', { name: 'Esci' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Si è verificato un errore',
    )
    expect(
      await screen.findByRole('heading', { name: 'La mia collezione' }),
    ).toBeInTheDocument()
  })

  it('forcedChangePasswordPageOffersLogout', async () => {
    replies['GET /auth/me'] = [200, me({ mustChangePassword: true })]
    replies['POST /auth/logout'] = [204]
    renderApp('/change-password')
    await userEvent.click(await screen.findByRole('button', { name: 'Esci' }))
    expect(
      await screen.findByRole('heading', { name: 'Accedi' }),
    ).toBeInTheDocument()
  })

  it('forcedChangePasswordLogoutFailureShowsError', async () => {
    replies['GET /auth/me'] = [200, me({ mustChangePassword: true })]
    replies['POST /auth/logout'] = [500, { detail: 'boom' }]
    renderApp('/change-password')
    await userEvent.click(await screen.findByRole('button', { name: 'Esci' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Si è verificato un errore',
    )
  })

  it('loginPageHasNoAxeViolations', async () => {
    const { container } = renderApp('/login')
    await screen.findByRole('heading', { name: 'Accedi' })
    const results = await axe(container)
    expect(results.violations).toEqual([])
  })
})
