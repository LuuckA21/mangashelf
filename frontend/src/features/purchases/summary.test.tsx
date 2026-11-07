import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../../App'
import type { SpendingSummary } from '../../api/types'
import { axe } from '../../test/axe'
import { formatChfGrouped } from './money'

const fetchMock = vi.fn()
let replies: Record<string, [number, unknown]> = {}

const months = (over: Record<number, object> = {}) =>
  Array.from({ length: 12 }, (_, i) => ({
    month: i + 1,
    spentCents: 0,
    upcomingCents: 0,
    hasLists: false,
    aboveAverage: false,
    ...over[i + 1],
  }))

const summary = (over: Partial<SpendingSummary> = {}): SpendingSummary => ({
  years: [2025, 2026],
  year: 2026,
  months: months({
    10: { spentCents: 164238, hasLists: true, aboveAverage: true },
    11: { upcomingCents: 14778, hasLists: true },
    12: { hasLists: true },
  }),
  monthTotals: {
    totalCents: 179016,
    averageCents: 89508,
    spentCents: 164238,
    upcomingCents: 14778,
  },
  byYear: [
    { year: 2025, spentCents: 100000, upcomingCents: 0, aboveAverage: false },
    {
      year: 2026,
      spentCents: 164238,
      upcomingCents: 14778,
      aboveAverage: true,
    },
  ],
  yearTotals: {
    totalCents: 279016,
    averageCents: 139508,
    spentCents: 264238,
    upcomingCents: 14778,
  },
  ...over,
})

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'], now: new Date(2026, 10, 7) })
  replies = {
    'GET /auth/me': [
      200,
      { id: '1', username: 'anna', role: 'USER', mustChangePassword: false },
    ],
    'GET /purchase-lists': [200, []],
    'GET /purchase-summary?year=2026': [200, summary()],
  }
  fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
    const [status, body] = replies[
      `${init.method} ${url.replace('/api/v1', '')}`
    ] ?? [404, { detail: `${init.method} ${url}` }]
    return new Response(JSON.stringify(body), { status })
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  fetchMock.mockReset()
})

function Where() {
  const l = useLocation()
  return <div data-testid="where">{l.pathname + l.search}</div>
}

function renderApp(path: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const { container } = render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
        <Where />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { client, container }
}

const where = () => screen.getByTestId('where').textContent
const monthsTable = async () =>
  (await screen.findByRole('heading', { name: 'Mesi' })).closest(
    '.summary-block',
  ) as HTMLElement
const rowOf = (block: HTMLElement, label: string) =>
  within(block).getByRole('row', { name: new RegExp(`^${label}`) })

describe('purchase summary', () => {
  it('formatChfGrouped', () => {
    expect(formatChfGrouped(0)).toBe('CHF 0.00')
    expect(formatChfGrouped(123456789)).toBe("CHF 1'234'567.89")
    expect(formatChfGrouped(1606210)).toBe("CHF 16'062.10")
  })

  it('tabsSwitchAndLiveInTheUrl', async () => {
    renderApp('/purchases')
    const user = userEvent.setup()
    expect(await screen.findByText('Nuova lista')).toBeVisible()
    await user.click(screen.getByRole('tab', { name: 'Riepilogo' }))
    expect(where()).toBe('/purchases?vista=riepilogo')
    expect(await screen.findByRole('heading', { name: 'Mesi' })).toBeVisible()
    expect(screen.queryByText('Nuova lista')).not.toBeInTheDocument()
    await user.click(screen.getByRole('tab', { name: 'Liste' }))
    expect(where()).toBe('/purchases')
    expect(screen.queryByRole('heading', { name: 'Mesi' })).toBeNull()
    expect(screen.getByText('Nuova lista')).toBeVisible()
  })

  it('arrowKeysMoveBetweenTabs', async () => {
    renderApp('/purchases')
    const user = userEvent.setup()
    const liste = await screen.findByRole('tab', { name: 'Liste' })
    liste.focus()
    await user.keyboard('{ArrowRight}')
    const riepilogo = screen.getByRole('tab', { name: 'Riepilogo' })
    expect(riepilogo).toHaveFocus()
    expect(riepilogo).toHaveAttribute('aria-selected', 'true')
    expect(where()).toBe('/purchases?vista=riepilogo')
    await user.keyboard('{ArrowLeft}')
    expect(screen.getByRole('tab', { name: 'Liste' })).toHaveFocus()
    expect(where()).toBe('/purchases')
  })

  it('requestsTheCurrentYearFirst', async () => {
    renderApp('/purchases?vista=riepilogo')
    await monthsTable()
    expect(fetchMock.mock.calls.map(([u]) => u)).toContain(
      '/api/v1/purchase-summary?year=2026',
    )
  })

  it('yearPickerChangesUrlAndData', async () => {
    replies['GET /purchase-summary?year=2025'] = [
      200,
      summary({
        year: 2025,
        months: months({ 3: { spentCents: 5000, hasLists: true } }),
      }),
    ]
    renderApp('/purchases?vista=riepilogo')
    const user = userEvent.setup()
    await user.selectOptions(await screen.findByLabelText('Anno'), '2025')
    expect(where()).toBe('/purchases?vista=riepilogo&anno=2025')
    const block = await monthsTable()
    await waitFor(() =>
      expect(
        within(rowOf(block, 'Marzo')).getByText('CHF 50.00'),
      ).toBeVisible(),
    )
  })

  it('unknownYearShowsTheServerYear', async () => {
    replies['GET /purchase-summary?year=2020'] = [200, summary()]
    renderApp('/purchases?vista=riepilogo&anno=2020')
    expect(await screen.findByLabelText('Anno')).toHaveValue('2026')
  })

  it('monthTableShowsBothColumnsAndTotals', async () => {
    renderApp('/purchases?vista=riepilogo')
    const block = await monthsTable()
    const oct = within(rowOf(block, 'Ottobre')).getAllByRole('cell')
    expect(oct.map((c) => c.textContent)).toEqual([
      expect.stringContaining('Ottobre'),
      "CHF 1'642.38",
      'CHF 0.00',
    ])
    for (const h of ['Mese', 'Speso', 'In arrivo']) {
      expect(within(block).getByRole('columnheader', { name: h })).toBeVisible()
    }
    const totale = rowOf(block, 'TOTALE')
    expect(within(totale).getByText("CHF 1'790.16")).toBeVisible()
    expect(within(rowOf(block, 'Media')).getByText('CHF 895.08')).toBeVisible()
    expect(
      within(rowOf(block, 'Totale speso')).getByText("CHF 1'642.38"),
    ).toBeVisible()
    expect(
      within(rowOf(block, 'Totale in arrivo')).getByText('CHF 147.78'),
    ).toBeVisible()
  })

  it('dashOnlyForMonthsWithoutLists', async () => {
    renderApp('/purchases?vista=riepilogo')
    const block = await monthsTable()
    expect(
      within(rowOf(block, 'Gennaio'))
        .getAllByRole('cell')
        .map((c) => c.textContent),
    ).toEqual(['Gennaio', '—', '—'])
    // a month with lists totalling zero shows amounts, not dashes
    expect(
      within(rowOf(block, 'Dicembre')).getAllByText('CHF 0.00'),
    ).toHaveLength(2)
  })

  it('aboveAverageRowsAreMarked', async () => {
    renderApp('/purchases?vista=riepilogo')
    const block = await monthsTable()
    const oct = rowOf(block, 'Ottobre')
    expect(oct).toHaveClass('above')
    expect(within(oct).getByText('sopra la media')).toBeInTheDocument()
    expect(rowOf(block, 'Novembre')).not.toHaveClass('above')
  })

  it('yearsTable', async () => {
    renderApp('/purchases?vista=riepilogo')
    await monthsTable()
    const block = screen
      .getByRole('heading', { name: 'Anni' })
      .closest('.summary-block') as HTMLElement
    expect(
      within(block).getByRole('columnheader', { name: 'Anno' }),
    ).toBeVisible()
    expect(within(rowOf(block, '2025')).getByText("CHF 1'000.00")).toBeVisible()
    expect(rowOf(block, '2026')).toHaveClass('above')
    expect(
      within(rowOf(block, 'TOTALE')).getByText("CHF 2'790.16"),
    ).toBeVisible()
  })

  it('noListsSaysSo', async () => {
    replies['GET /purchase-summary?year=2026'] = [
      200,
      summary({
        years: [],
        year: null,
        byYear: [],
        months: months(),
      }),
    ]
    renderApp('/purchases?vista=riepilogo')
    expect(await screen.findByText('Ancora nessuna spesa')).toBeVisible()
    expect(screen.queryByLabelText('Anno')).toBeNull()
  })

  it('summaryRefreshesAfterAListChange', async () => {
    replies['POST /purchase-lists'] = [
      201,
      {
        id: 'n',
        description: 'x',
        month: 1,
        year: 2026,
        closed: false,
        itemCount: 0,
        unbookedCount: 0,
        totalCents: 0,
      },
    ]
    const { client } = renderApp('/purchases')
    const spy = vi.spyOn(client, 'invalidateQueries')
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Descrizione'), 'x')
    await user.click(screen.getByRole('button', { name: 'Crea lista' }))
    await waitFor(() => {
      const keys = spy.mock.calls.map(([f]) => (f?.queryKey as string[])[0])
      expect(keys).toEqual(
        expect.arrayContaining(['purchase-lists', 'purchase-summary']),
      )
    })
  })

  it.each(['anno=1999', 'anno=', 'anno=2200', 'anno=abc'])(
    'badYearInTheUrlFallsBackToTheCurrentYear (%s)',
    async (q) => {
      renderApp(`/purchases?vista=riepilogo&${q}`)
      expect(await screen.findByLabelText('Anno')).toHaveValue('2026')
      const urls = fetchMock.mock.calls.map(([u]) => u)
      expect(urls).toContain('/api/v1/purchase-summary?year=2026')
      expect(urls.filter((u) => u.includes('purchase-summary'))).toHaveLength(1)
    },
  )

  it('yearSelectKeepsFocusWhileTheNewYearLoads', async () => {
    replies['GET /purchase-summary?year=2025'] = [200, summary({ year: 2025 })]
    renderApp('/purchases?vista=riepilogo')
    const user = userEvent.setup()
    const select = await screen.findByLabelText('Anno')
    await user.selectOptions(select, '2025')
    expect(screen.getByLabelText('Anno')).toBe(select)
    expect(select).toHaveFocus()
    await waitFor(() => expect(select).toHaveValue('2025'))
  })

  const keysOf = (spy: { mock: { calls: unknown[][] } }) =>
    spy.mock.calls.map(([f]) => (f as { queryKey: string[] }).queryKey[0])

  it('listChangeInvalidatesTheSummary', async () => {
    replies['GET /purchase-lists/l1'] = [
      200,
      {
        id: 'l1',
        description: 'Manga Ottobre',
        month: 10,
        year: 2026,
        closed: true,
        items: [],
        subtotalCents: 0,
        discountCents: 0,
        discountAuto: true,
        pointsDiscountCents: 0,
        totalCents: 0,
      },
    ]
    replies['POST /purchase-lists/l1/reopen'] = [
      200,
      { ...(replies['GET /purchase-lists/l1'][1] as object), closed: false },
    ]
    const { client } = renderApp('/purchases/l1')
    const user = userEvent.setup()
    const reopen = await screen.findByRole('button', { name: 'Riapri lista' })
    const spy = vi.spyOn(client, 'invalidateQueries')
    await user.click(reopen)
    await waitFor(() => expect(keysOf(spy)).toContain('purchase-summary'))
  })

  it('savingPurchaseSettingsInvalidatesTheSummary', async () => {
    replies['GET /account/purchase-settings'] = [
      200,
      { chfRate: 1.2, discountPercent: 10 },
    ]
    replies['PUT /account/purchase-settings'] = [
      200,
      { chfRate: 1.2, discountPercent: 15 },
    ]
    const { client } = renderApp('/account')
    const spy = vi.spyOn(client, 'invalidateQueries')
    const user = userEvent.setup()
    await user.click(
      (await screen.findAllByRole('button', { name: 'Salva' })).at(-1)!,
    )
    await waitFor(() => expect(keysOf(spy)).toContain('purchase-summary'))
  })

  it('axeCleanOnRiepilogo', async () => {
    const { container } = renderApp('/purchases?vista=riepilogo')
    await monthsTable()
    expect(
      (await axe(container.firstElementChild as HTMLElement)).violations,
    ).toEqual([])
  })
})
