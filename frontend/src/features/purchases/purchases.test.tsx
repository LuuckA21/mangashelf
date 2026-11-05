import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../../App'
import type { ListSummary } from '../../api/types'
import { axe } from '../../test/axe'
import { formatChf, formatEur, parseAmount, proposeChf } from './money'

const fetchMock = vi.fn()
type Reply = [status: number, body?: unknown]
let replies: Record<string, Reply | (() => Reply | Promise<Reply>)> = {}

const summary = (over: Partial<ListSummary> = {}): ListSummary => ({
  id: 'l1',
  description: 'Manga Ottobre',
  month: 10,
  year: 2026,
  closed: false,
  itemCount: 24,
  unbookedCount: 2,
  totalCents: 21690,
  ...over,
})

beforeEach(() => {
  replies = {
    'GET /auth/me': [
      200,
      { id: '1', username: 'anna', role: 'USER', mustChangePassword: false },
    ],
    'GET /purchase-lists': [200, [summary()]],
    'GET /account/purchase-settings': [
      200,
      { chfRate: 1.2, discountPercent: 10 },
    ],
    'GET /collection?page=0&size=20': [
      200,
      {
        content: [],
        page: { size: 20, number: 0, totalElements: 0, totalPages: 1 },
      },
    ],
    'GET /collection/stats': [200, {}],
  }
  fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
    const r = replies[`${init.method} ${url.replace('/api/v1', '')}`] ?? [
      404,
      { detail: `${init.method} ${url}` },
    ]
    const [status, body] = typeof r === 'function' ? await r() : r
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

function Where() {
  return <div data-testid="where">{useLocation().pathname}</div>
}

function renderApp(path: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
        <Where />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const bodyOf = (method: string, url: string) => {
  const call = fetchMock.mock.calls.find(
    ([u, i]) => u === `/api/v1${url}` && i.method === method,
  )
  return call && JSON.parse(call[1].body)
}

describe('purchases', () => {
  it('navShowsAcquisti', async () => {
    renderApp('/')
    const link = await screen.findByRole('link', { name: 'Acquisti' })
    expect(link).toHaveAttribute('href', '/purchases')
  })

  it('listsShowDescriptionMonthStateCountsAndTotal', async () => {
    renderApp('/purchases')
    expect(
      await screen.findByText('Manga Ottobre · ottobre 2026'),
    ).toBeVisible()
    expect(screen.getByText('Aperta')).toBeVisible()
    expect(screen.getByText('24 volumi, 2 non prenotati')).toBeVisible()
    expect(screen.getByText('CHF 216.90')).toBeVisible()
  })

  it('createListGoesToItsPage', async () => {
    replies['POST /purchase-lists'] = [201, summary({ id: 'new1' })]
    replies['GET /purchase-lists/new1'] = [
      200,
      {
        id: 'new1',
        description: 'Manga Novembre',
        month: 11,
        year: 2026,
        closed: false,
        items: [],
        subtotalCents: 0,
        discountCents: 0,
        discountAuto: true,
        pointsDiscountCents: 0,
        totalCents: 0,
      },
    ]
    renderApp('/purchases')
    const user = userEvent.setup()
    await screen.findByText('Manga Ottobre · ottobre 2026')
    await user.type(screen.getByLabelText('Descrizione'), 'Manga Novembre')
    await user.clear(screen.getByLabelText('Mese'))
    await user.type(screen.getByLabelText('Mese'), '2026-11')
    await user.click(screen.getByRole('button', { name: 'Crea lista' }))
    await waitFor(() =>
      expect(screen.getByTestId('where')).toHaveTextContent('/purchases/new1'),
    )
    expect(bodyOf('POST', '/purchase-lists')).toEqual({
      description: 'Manga Novembre',
      month: 11,
      year: 2026,
    })
    expect(await screen.findByText(/Manga Novembre/)).toBeVisible()
  })

  it('createListRequiresDescription', async () => {
    replies['POST /purchase-lists'] = [
      400,
      {
        detail: 'Dati non validi',
        errors: [{ field: 'description', message: 'non deve essere vuoto' }],
      },
    ]
    renderApp('/purchases')
    const user = userEvent.setup()
    await screen.findByText('Manga Ottobre · ottobre 2026')
    await user.type(screen.getByLabelText('Descrizione'), '   ')
    await user.click(screen.getByRole('button', { name: 'Crea lista' }))
    expect(await screen.findByText('non deve essere vuoto')).toBeVisible()
    expect(screen.getByTestId('where')).toHaveTextContent('/purchases')
  })

  it('settingsShowAndSave', async () => {
    replies['PUT /account/purchase-settings'] = [
      200,
      { chfRate: 1.25, discountPercent: 15 },
    ]
    renderApp('/account')
    const user = userEvent.setup()
    const rate = await screen.findByLabelText('Fattore EUR→CHF')
    const disc = screen.getByLabelText('Sconto predefinito (%)')
    await waitFor(() => expect(rate).toHaveValue('1.20'))
    expect(disc).toHaveValue('10')
    await user.clear(rate)
    await user.type(rate, '1,25')
    await user.clear(disc)
    await user.type(disc, '15')
    await user.click(screen.getByRole('button', { name: 'Salva' }))
    await waitFor(() =>
      expect(bodyOf('PUT', '/account/purchase-settings')).toEqual({
        chfRate: 1.25,
        discountPercent: 15,
      }),
    )
  })

  it('moneyHelpers', () => {
    expect(proposeChf(790, 1.2)).toBe(950)
    expect(proposeChf(995, 1.2)).toBe(1190)
    expect(parseAmount('7,90')).toBe(790)
    expect(parseAmount('7.90')).toBe(790)
    expect(parseAmount('7')).toBe(700)
    expect(parseAmount('-1')).toBeNull()
    expect(parseAmount('abc')).toBeNull()
    expect(formatChf(950)).toBe('CHF 9.50')
    expect(formatEur(790)).toBe('EUR 7.90')
  })

  it('listsPageHasNoA11yViolations', async () => {
    const { container } = renderApp('/purchases')
    await screen.findByText('Manga Ottobre · ottobre 2026')
    expect((await axe(container)).violations).toEqual([])
  })
})
