import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../../App'
import type { ItemView, ListDetail, ListSummary } from '../../api/types'
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
  const view = render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
        <Where />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { ...view, client }
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

const item = (over: Partial<ItemView> = {}): ItemView => ({
  id: 'i1',
  editionId: 'e1',
  seriesId: 's1',
  seriesTitle: 'Berserk',
  editionName: 'Deluxe',
  editionVolumeCount: 41,
  volume: 114,
  releaseDate: '2026-08-20',
  priceEurCents: 590,
  priceChfCents: 710,
  booked: false,
  alsoIn: [],
  ...over,
})

const detail = (
  items: ItemView[],
  over: Partial<ListDetail> = {},
): ListDetail => ({
  id: 'l1',
  description: 'Manga Ottobre',
  month: 10,
  year: 2026,
  closed: false,
  items,
  subtotalCents: 1000,
  discountCents: 100,
  discountAuto: true,
  pointsDiscountCents: 0,
  totalCents: 900,
  ...over,
})

const rowOf = (text: string) =>
  screen.getByText(text).closest('li') as HTMLElement

describe('purchase list detail', () => {
  const open = async (d: ListDetail) => {
    replies['GET /purchase-lists/l1'] = [200, d]
    const view = renderApp('/purchases/l1')
    await screen.findByRole('heading', { name: /Manga Ottobre/ })
    return view
  }

  it('rowsGroupedByDateWithUndatedLast', async () => {
    await open(
      detail([
        item({ id: 'a', releaseDate: null, seriesTitle: 'Zeta' }),
        item({ id: 'b', releaseDate: '2026-09-05', seriesTitle: 'Beta' }),
        item({ id: 'c', releaseDate: '2026-08-20', seriesTitle: 'Alfa' }),
      ]),
    )
    const heads = screen
      .getAllByRole('heading', { level: 2 })
      .map((h) => h.textContent)
    expect(heads).toEqual([
      'Aggiungi volume',
      '20 agosto',
      '5 settembre',
      'Data da definire',
    ])
  })

  it('unicoForSingleVolumeEdition', async () => {
    await open(
      detail([
        item({
          id: 'a',
          seriesTitle: 'Monster',
          editionVolumeCount: 1,
          volume: 1,
        }),
      ]),
    )
    expect(within(rowOf('Monster')).getByText('Unico')).toBeVisible()
  })

  it('alsoInNoteShown', async () => {
    await open(detail([item({ alsoIn: ['Manga Settembre'] })]))
    expect(screen.getByText('Già nella lista «Manga Settembre»')).toBeVisible()
  })

  it('addRowUsesProposals', async () => {
    replies['GET /series?page=0&size=20&q=berserk'] = [
      200,
      {
        content: [{ id: 's1', title: 'Berserk', editionCount: 1 }],
        page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
      },
    ]
    replies['GET /series/s1'] = [
      200,
      {
        id: 's1',
        title: 'Berserk',
        editions: [
          {
            id: 'e1',
            seriesId: 's1',
            seriesTitle: 'Berserk',
            name: 'Deluxe',
            volumeCount: 41,
          },
        ],
      },
    ]
    replies['GET /purchase-lists/l1/proposal?editionId=e1'] = [
      200,
      { volume: 114, priceEurCents: 590, releaseDate: null },
    ]
    replies['POST /purchase-lists/l1/items'] = [201, detail([item()])]
    await open(detail([]))
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Cerca serie'), 'berserk')
    await user.click(screen.getByRole('button', { name: 'Cerca' }))
    await user.click(await screen.findByRole('button', { name: /Berserk/ }))
    await user.click(await screen.findByRole('button', { name: /Deluxe/ }))
    await waitFor(() =>
      expect(screen.getByLabelText('Volume')).toHaveValue(114),
    )
    expect(screen.getByLabelText('Prezzo EUR')).toHaveValue('5.90')
    expect(screen.getByLabelText('Prezzo CHF')).toHaveValue('7.10')
    await user.click(screen.getByRole('button', { name: 'Aggiungi' }))
    await waitFor(() =>
      expect(bodyOf('POST', '/purchase-lists/l1/items')).toEqual({
        editionId: 'e1',
        volume: 114,
        releaseDate: null,
        priceEurCents: 590,
        priceChfCents: 710,
        booked: false,
      }),
    )
    expect(await screen.findByText('EUR 5.90')).toBeVisible()
  })

  it('editedChfIsNotOverwrittenByEur', async () => {
    replies['PATCH /purchase-lists/l1/items/i1'] = [200, detail([item()])]
    await open(detail([item()]))
    const user = userEvent.setup()
    const row = within(rowOf('Berserk'))
    await user.click(row.getByRole('button', { name: 'Modifica' }))
    const eur = row.getByLabelText('Prezzo EUR')
    const chf = row.getByLabelText('Prezzo CHF')
    await user.clear(eur)
    await user.type(eur, '10')
    expect(chf).toHaveValue('12.00')
    await user.clear(chf)
    await user.type(chf, '11')
    await user.clear(eur)
    await user.type(eur, '20')
    expect(chf).toHaveValue('11')
    await user.click(screen.getByRole('button', { name: 'Salva' }))
    await waitFor(() =>
      expect(bodyOf('PATCH', '/purchase-lists/l1/items/i1')).toMatchObject({
        priceEurCents: 2000,
        priceChfCents: 1100,
      }),
    )
  })

  it('bookedToggleSendsPatchAndRefreshesCollection', async () => {
    replies['PATCH /purchase-lists/l1/items/i1'] = [
      200,
      detail([item({ booked: true })]),
    ]
    const { client } = await open(detail([item()]))
    const spy = vi.spyOn(client, 'invalidateQueries')
    const user = userEvent.setup()
    const toggle = within(rowOf('Berserk')).getByRole('button', {
      name: 'Prenotata',
    })
    expect(toggle).toHaveAttribute('aria-pressed', 'false')
    await user.click(toggle)
    await waitFor(() =>
      expect(bodyOf('PATCH', '/purchase-lists/l1/items/i1')).toEqual({
        booked: true,
      }),
    )
    await waitFor(() =>
      expect(
        within(rowOf('Berserk')).getByRole('button', { name: 'Prenotata' }),
      ).toHaveAttribute('aria-pressed', 'true'),
    )
    expect(rowOf('Berserk')).toHaveClass('booked')
    const keys = spy.mock.calls.map(([f]) => (f?.queryKey as string[])[0])
    expect(keys).toEqual(
      expect.arrayContaining(['collection', 'edition', 'collection-entry']),
    )
  })

  it('editAndDeleteRow', async () => {
    replies['DELETE /purchase-lists/l1/items/i1'] = [200, detail([])]
    await open(detail([item()]))
    const user = userEvent.setup()
    await user.click(
      within(rowOf('Berserk')).getByRole('button', { name: 'Elimina' }),
    )
    expect(fetchMock.mock.calls.some(([, i]) => i.method === 'DELETE')).toBe(
      false,
    )
    await user.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    await waitFor(() =>
      expect(screen.queryByText('EUR 5.90')).not.toBeInTheDocument(),
    )
  })

  it('totalsAndDiscountOverride', async () => {
    replies['PATCH /purchase-lists/l1'] = [
      200,
      detail([item()], { discountCents: 500, discountAuto: false }),
    ]
    await open(detail([item()]))
    const user = userEvent.setup()
    expect(screen.getByText('CHF 10.00')).toBeVisible()
    expect(screen.getByText('CHF 9.00')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Modifica sconto' }))
    await user.type(screen.getByLabelText('Sconto (CHF)'), '5.00')
    await user.click(screen.getByRole('button', { name: 'Salva sconto' }))
    await waitFor(() =>
      expect(bodyOf('PATCH', '/purchase-lists/l1')).toEqual({
        discountCents: 500,
      }),
    )
    fetchMock.mockClear()
    await user.click(
      await screen.findByRole('button', { name: 'Torna al 10%' }),
    )
    await waitFor(() =>
      expect(bodyOf('PATCH', '/purchase-lists/l1')).toEqual({
        discountAuto: true,
      }),
    )
  })

  it('pointsDiscountSaved', async () => {
    replies['PATCH /purchase-lists/l1'] = [
      200,
      detail([item()], { pointsDiscountCents: 250 }),
    ]
    await open(detail([item()]))
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Sconto punti (CHF)'), '2,50')
    await user.click(screen.getByRole('button', { name: 'Salva punti' }))
    await waitFor(() =>
      expect(bodyOf('PATCH', '/purchase-lists/l1')).toEqual({
        pointsDiscountCents: 250,
      }),
    )
  })

  it('discountFieldErrorShownOnInput', async () => {
    replies['PATCH /purchase-lists/l1'] = [
      400,
      {
        detail: 'Dati non validi',
        errors: [{ field: 'discountCents', message: 'supera il subtotale' }],
      },
    ]
    await open(detail([item()]))
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Modifica sconto' }))
    await user.type(screen.getByLabelText('Sconto (CHF)'), '99')
    await user.click(screen.getByRole('button', { name: 'Salva sconto' }))
    const msg = await screen.findByText('supera il subtotale')
    expect(screen.getByLabelText('Sconto (CHF)')).toHaveAccessibleDescription(
      msg.textContent!,
    )
  })

  it('detailPageHasNoA11yViolations', async () => {
    const { container } = await open(
      detail([item(), item({ id: 'i2', releaseDate: null, booked: true })]),
    )
    expect((await axe(container)).violations).toEqual([])
  })
})
