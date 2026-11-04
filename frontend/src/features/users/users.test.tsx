import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../../App'
import type { UserResponse } from '../../api/types'
import { axe } from '../../test/axe'

const fetchMock = vi.fn()
type Reply = [status: number, body?: unknown]
let replies: Record<string, Reply | (() => Reply)> = {}

const user = (over: Partial<UserResponse> = {}): UserResponse => ({
  id: 'u1',
  username: 'anna',
  role: 'ADMIN',
  mustChangePassword: false,
  createdAt: '2026-11-01T10:00:00Z',
  ...over,
})
const bruno = user({ id: 'u2', username: 'bruno', role: 'USER' })
const pageOf = (content: unknown[], totalPages = 1) => ({
  content,
  page: { size: 20, number: 0, totalElements: content.length, totalPages },
})

beforeEach(() => {
  replies = {
    'GET /auth/me': [
      200,
      { id: 'u1', username: 'anna', role: 'ADMIN', mustChangePassword: false },
    ],
    'GET /users?page=0&size=20': [200, pageOf([user(), bruno])],
  }
  fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
    const r = replies[`${init.method} ${url.replace('/api/v1', '')}`] ?? [
      404,
      { detail: `${init.method} ${url}` },
    ]
    const [status, body] = typeof r === 'function' ? r() : r
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

function renderApp() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/admin/users']}>
        <AppRoutes />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const calls = (method: string, url: string) =>
  fetchMock.mock.calls.filter(
    (c) => c[1].method === method && c[0] === `/api/v1${url}`,
  )

const rowOf = async (name: string) =>
  (await screen.findByRole('cell', { name: new RegExp(`^${name}`) })).closest(
    'tr',
  ) as HTMLElement

describe('users page', () => {
  it('labelsEveryCellForTheMobileLayout', async () => {
    renderApp()
    await screen.findByText('bruno')
    const cells = screen.getAllByRole('cell')
    expect(cells).toHaveLength(8)
    for (const c of cells) expect(c).toHaveAttribute('data-label')
    expect(cells[0]).toHaveAttribute('data-label', 'Nome utente')
  })

  it('createsUserWithTemporaryPassword', async () => {
    replies['POST /users'] = [201, user({ id: 'u3', username: 'carla' })]
    renderApp()
    await userEvent.type(await screen.findByLabelText('Nome utente'), 'carla')
    await userEvent.type(
      screen.getByLabelText('Password temporanea'),
      'una-password-lunga',
    )
    await userEvent.selectOptions(screen.getByLabelText('Ruolo'), 'ADMIN')
    await userEvent.click(screen.getByRole('button', { name: 'Crea utente' }))
    await vi.waitFor(() => expect(calls('POST', '/users')).toHaveLength(1))
    expect(JSON.parse(calls('POST', '/users')[0][1].body)).toEqual({
      username: 'carla',
      temporaryPassword: 'una-password-lunga',
      role: 'ADMIN',
    })
    await vi.waitFor(() =>
      expect(calls('GET', '/users?page=0&size=20')).toHaveLength(2),
    )
    expect(screen.getByLabelText('Nome utente')).toHaveValue('')
  })

  it('invalidUsernameShowsFieldError', async () => {
    replies['POST /users'] = [
      400,
      {
        detail: 'Dati non validi',
        errors: [{ field: 'username', message: 'Da 3 a 32 caratteri' }],
      },
    ]
    renderApp()
    const input = await screen.findByLabelText('Nome utente')
    await userEvent.type(input, 'a b')
    await userEvent.type(
      screen.getByLabelText('Password temporanea'),
      'x'.repeat(12),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Crea utente' }))
    const msg = await screen.findByText('Da 3 a 32 caratteri')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-describedby', msg.id)
    expect(input).toHaveFocus()
  })

  it('duplicateUsernameShowsDetail', async () => {
    replies['POST /users'] = [409, { detail: 'Nome utente già in uso' }]
    renderApp()
    await userEvent.type(await screen.findByLabelText('Nome utente'), 'bruno')
    await userEvent.type(
      screen.getByLabelText('Password temporanea'),
      'x'.repeat(12),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Crea utente' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Nome utente già in uso')
    expect(alert).toHaveClass('error')
  })

  it('ownRowHasNoRoleOrDeleteControls', async () => {
    renderApp()
    const own = await rowOf('anna')
    expect(within(own).queryByRole('combobox')).not.toBeInTheDocument()
    expect(within(own).queryByRole('button')).not.toBeInTheDocument()
    expect(within(await rowOf('bruno')).getByRole('combobox')).toBeEnabled()
  })

  it('changesRole', async () => {
    replies['PATCH /users/u2'] = [200, user({ id: 'u2', role: 'ADMIN' })]
    renderApp()
    await userEvent.selectOptions(
      await screen.findByLabelText('Ruolo di bruno'),
      'ADMIN',
    )
    await vi.waitFor(() => expect(calls('PATCH', '/users/u2')).toHaveLength(1))
    expect(JSON.parse(calls('PATCH', '/users/u2')[0][1].body)).toEqual({
      role: 'ADMIN',
    })
  })

  it('lastAdminConflictShown', async () => {
    replies['GET /users?page=0&size=20'] = [
      200,
      pageOf([user(), user({ id: 'u2', username: 'bruno' })]),
    ]
    replies['PATCH /users/u2'] = [409, { detail: 'Serve almeno un admin' }]
    renderApp()
    await userEvent.selectOptions(
      await screen.findByLabelText('Ruolo di bruno'),
      'USER',
    )
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Serve almeno un admin')
    expect(alert).toHaveClass('error')
    expect(screen.getByLabelText('Ruolo di bruno')).toHaveValue('ADMIN')
  })

  it('resetsPassword', async () => {
    replies['POST /users/u2/reset-password'] = [204]
    renderApp()
    const row = await rowOf('bruno')
    await userEvent.click(
      within(row).getByRole('button', { name: 'Reimposta password' }),
    )
    await userEvent.type(
      screen.getByLabelText('Password temporanea per bruno'),
      'nuova-password-lunga',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Reimposta' }))
    await vi.waitFor(() =>
      expect(calls('POST', '/users/u2/reset-password')).toHaveLength(1),
    )
    expect(
      JSON.parse(calls('POST', '/users/u2/reset-password')[0][1].body),
    ).toEqual({ temporaryPassword: 'nuova-password-lunga' })
    expect(
      await screen.findByText('Password reimpostata per bruno'),
    ).toBeInTheDocument()
  })

  it('resetShowsFieldError', async () => {
    replies['POST /users/u2/reset-password'] = [
      400,
      {
        detail: 'Dati non validi',
        errors: [{ field: 'temporaryPassword', message: 'Troppo corta' }],
      },
    ]
    renderApp()
    await userEvent.click(
      within(await rowOf('bruno')).getByRole('button', {
        name: 'Reimposta password',
      }),
    )
    const input = screen.getByLabelText('Password temporanea per bruno')
    await userEvent.type(input, 'corta')
    await userEvent.click(screen.getByRole('button', { name: 'Reimposta' }))
    expect(await screen.findByText('Troppo corta')).toBeInTheDocument()
    expect(input).toHaveAttribute('aria-invalid', 'true')
  })

  it('deletesUserAfterConfirmation', async () => {
    replies['DELETE /users/u2'] = [204]
    renderApp()
    await userEvent.click(
      within(await rowOf('bruno')).getByRole('button', { name: 'Elimina' }),
    )
    expect(calls('DELETE', '/users/u2')).toHaveLength(0)
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    await vi.waitFor(() => expect(calls('DELETE', '/users/u2')).toHaveLength(1))
  })

  it('deleteConflictShown', async () => {
    replies['DELETE /users/u2'] = [409, { detail: 'Ultimo admin' }]
    renderApp()
    await userEvent.click(
      within(await rowOf('bruno')).getByRole('button', { name: 'Elimina' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Ultimo admin')
  })

  it('pagesWithPrevNext', async () => {
    replies['GET /users?page=0&size=20'] = [200, pageOf([user()], 2)]
    replies['GET /users?page=1&size=20'] = [200, pageOf([bruno], 2)]
    renderApp()
    await screen.findByText('Pagina 1 di 2')
    await userEvent.click(screen.getByRole('button', { name: 'Successiva' }))
    expect(await screen.findByText('Pagina 2 di 2')).toBeInTheDocument()
    expect(await screen.findByText('bruno')).toBeInTheDocument()
  })

  it('stepsBackWhenTheLastPageEmpties', async () => {
    replies['GET /users?page=0&size=20'] = [200, pageOf([user()], 2)]
    replies['GET /users?page=1&size=20'] = [200, pageOf([bruno], 2)]
    replies['DELETE /users/u2'] = [204]
    renderApp()
    await userEvent.click(
      await screen.findByRole('button', { name: 'Successiva' }),
    )
    await userEvent.click(
      within(await rowOf('bruno')).getByRole('button', { name: 'Elimina' }),
    )
    replies['GET /users?page=0&size=20'] = [200, pageOf([user()], 1)]
    replies['GET /users?page=1&size=20'] = [
      200,
      {
        content: [],
        page: { size: 20, number: 1, totalElements: 1, totalPages: 1 },
      },
    ]
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    expect(await rowOf('anna')).toBeInTheDocument()
    expect(screen.queryByText('bruno')).not.toBeInTheDocument()
  })

  it('hasNoAccessibilityViolations', async () => {
    const { container } = renderApp()
    await screen.findByText('bruno')
    expect((await axe(container)).violations).toEqual([])
  })
})
