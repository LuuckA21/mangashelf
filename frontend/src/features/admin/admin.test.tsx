import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../../App'
import { axe } from '../../test/axe'

const fetchMock = vi.fn()
type Reply = [status: number, body?: unknown]
let replies: Record<string, Reply | (() => Reply)> = {}

const me = (role: 'ADMIN' | 'USER') => ({
  id: 'u1',
  username: 'anna',
  role,
  mustChangePassword: false,
})
const star = { id: 'p1', name: 'Star Comics' }
const panini = { id: 'p2', name: 'Panini' }

beforeEach(() => {
  replies = {
    'GET /auth/me': [200, me('ADMIN')],
    'GET /publishers': [200, [panini, star]],
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

function renderApp(path = '/admin') {
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

const rowOf = async (name: string) =>
  (await screen.findByText(name)).closest('li') as HTMLElement

describe('admin page', () => {
  it('adminNavOnlyForAdmins', async () => {
    renderApp()
    expect(
      await screen.findByRole('link', { name: 'Amministrazione' }),
    ).toHaveAttribute('href', '/admin')
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Amministrazione' }),
    ).toBeInTheDocument()
  })

  it('userSeesNoLinkAndNotFound', async () => {
    replies['GET /auth/me'] = [200, me('USER')]
    renderApp()
    expect(
      await screen.findByRole('heading', { name: 'Pagina non trovata' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('link', { name: 'Amministrazione' }),
    ).not.toBeInTheDocument()
  })

  it('showsPublishersInServerOrder', async () => {
    renderApp()
    await screen.findByText('Star Comics')
    const items = screen.getAllByRole('listitem')
    expect(items.map((i) => i.querySelector('.name')?.textContent)).toEqual([
      'Panini',
      'Star Comics',
    ])
  })

  it('onlyOneNavLinkIsCurrent', async () => {
    renderApp('/admin/users')
    const users = await screen.findByRole('link', { name: 'Utenti' })
    expect(users).toHaveAttribute('aria-current', 'page')
    expect(
      screen.getByRole('link', { name: 'Amministrazione' }),
    ).not.toHaveAttribute('aria-current')
  })

  it('addClearsTheDeleteError', async () => {
    replies['DELETE /publishers/p1'] = [409, { detail: 'In uso' }]
    replies['POST /publishers'] = [201, { id: 'p3', name: 'Jpop' }]
    renderApp()
    await userEvent.click(
      await screen.findByRole('button', { name: 'Elimina Star Comics' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('In uso')
    await userEvent.type(screen.getByLabelText('Nome'), 'Jpop')
    await userEvent.click(screen.getByRole('button', { name: 'Aggiungi' }))
    await vi.waitFor(() =>
      expect(screen.queryByRole('alert')).not.toBeInTheDocument(),
    )
  })

  it('addPublisher', async () => {
    replies['POST /publishers'] = [201, { id: 'p3', name: 'Jpop' }]
    renderApp()
    await userEvent.type(await screen.findByLabelText('Nome'), 'Jpop')
    await userEvent.click(screen.getByRole('button', { name: 'Aggiungi' }))
    await vi.waitFor(() => expect(calls('POST', '/publishers')).toHaveLength(1))
    expect(JSON.parse(calls('POST', '/publishers')[0][1].body)).toEqual({
      name: 'Jpop',
    })
    await vi.waitFor(() => expect(calls('GET', '/publishers')).toHaveLength(2))
    expect(screen.getByLabelText('Nome')).toHaveValue('')
  })

  it('addDuplicateShowsFieldError', async () => {
    replies['POST /publishers'] = [
      400,
      {
        detail: 'Dati non validi',
        errors: [{ field: 'name', message: 'Editore già presente' }],
      },
    ]
    renderApp()
    const input = await screen.findByLabelText('Nome')
    await userEvent.type(input, 'panini')
    await userEvent.click(screen.getByRole('button', { name: 'Aggiungi' }))
    const msg = await screen.findByText('Editore già presente')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-describedby', msg.id)
    expect(input).toHaveFocus()
  })

  it('renamePublisher', async () => {
    replies['PATCH /publishers/p1'] = [200, { id: 'p1', name: 'Star' }]
    renderApp()
    const row = await rowOf('Star Comics')
    await userEvent.click(
      within(row).getByRole('button', { name: 'Rinomina Star Comics' }),
    )
    const input = screen.getByLabelText('Nuovo nome di Star Comics')
    expect(input).toHaveValue('Star Comics')
    await userEvent.clear(input)
    await userEvent.type(input, 'Star')
    await userEvent.click(screen.getByRole('button', { name: 'Salva' }))
    await vi.waitFor(() =>
      expect(calls('PATCH', '/publishers/p1')).toHaveLength(1),
    )
    expect(JSON.parse(calls('PATCH', '/publishers/p1')[0][1].body)).toEqual({
      name: 'Star',
    })
  })

  it('renameAndCancel', async () => {
    renderApp()
    const row = await rowOf('Star Comics')
    await userEvent.click(
      within(row).getByRole('button', { name: 'Rinomina Star Comics' }),
    )
    await userEvent.type(
      screen.getByLabelText('Nuovo nome di Star Comics'),
      'xx',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Annulla' }))
    expect(
      screen.queryByLabelText('Nuovo nome di Star Comics'),
    ).not.toBeInTheDocument()
    expect(calls('PATCH', '/publishers/p1')).toHaveLength(0)
    expect(screen.getByText('Star Comics')).toBeInTheDocument()
  })

  it('renameDuplicateShowsFieldError', async () => {
    replies['PATCH /publishers/p1'] = [
      400,
      {
        detail: 'Dati non validi',
        errors: [{ field: 'name', message: 'Editore già presente' }],
      },
    ]
    renderApp()
    await userEvent.click(
      await screen.findByRole('button', { name: 'Rinomina Star Comics' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Salva' }))
    expect(await screen.findByText('Editore già presente')).toBeInTheDocument()
    expect(screen.getByLabelText('Nuovo nome di Star Comics')).toHaveAttribute(
      'aria-invalid',
      'true',
    )
  })

  it('deleteAsksConfirmation', async () => {
    replies['DELETE /publishers/p1'] = [204]
    renderApp()
    const row = await rowOf('Star Comics')
    await userEvent.click(
      within(row).getByRole('button', { name: 'Elimina Star Comics' }),
    )
    expect(calls('DELETE', '/publishers/p1')).toHaveLength(0)
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    await vi.waitFor(() =>
      expect(calls('DELETE', '/publishers/p1')).toHaveLength(1),
    )
  })

  it('deleteInUseShowsMessage', async () => {
    replies['DELETE /publishers/p1'] = [
      409,
      { detail: "L'editore è usato da alcune edizioni" },
    ]
    renderApp()
    await userEvent.click(
      await screen.findByRole('button', { name: 'Elimina Star Comics' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent("L'editore è usato da alcune edizioni")
    expect(alert).toHaveClass('error')
  })

  it('cancellingADeleteClearsTheMessage', async () => {
    replies['DELETE /publishers/p1'] = [
      409,
      { detail: "L'editore è usato da alcune edizioni" },
    ]
    renderApp()
    const del = await screen.findByRole('button', {
      name: 'Elimina Star Comics',
    })
    await userEvent.click(del)
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    await screen.findByRole('alert')
    await userEvent.click(
      screen.getByRole('button', { name: 'Elimina Star Comics' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Annulla' }))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('hasNoAccessibilityViolations', async () => {
    const { container } = renderApp()
    await screen.findByText('Star Comics')
    expect((await axe(container)).violations).toEqual([])
  })
})
