import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
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

  it('listCountsUseSingularUnbooked', async () => {
    replies['GET /purchase-lists'] = [
      200,
      [
        summary({ itemCount: 4, unbookedCount: 1 }),
        summary({ id: 'l9', itemCount: 1, unbookedCount: 1 }),
      ],
    ]
    renderApp('/purchases')
    expect(await screen.findByText('4 volumi, 1 non prenotato')).toBeVisible()
    expect(screen.getByText('1 volume, 1 non prenotato')).toBeVisible()
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

  const pickBerserk = async (user: ReturnType<typeof userEvent.setup>) => {
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
    await user.type(screen.getByLabelText('Cerca serie'), 'berserk')
    await user.click(screen.getByRole('button', { name: 'Cerca' }))
    await user.click(await screen.findByRole('button', { name: /Berserk/ }))
    await user.click(await screen.findByRole('button', { name: /Deluxe/ }))
  }

  it('pickingEditionRecomputesHandEditedChf', async () => {
    replies['GET /purchase-lists/l1/proposal?editionId=e1'] = [
      200,
      { volume: 114, priceEurCents: 590, releaseDate: null },
    ]
    await open(detail([]))
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Prezzo CHF'), '9')
    await pickBerserk(user)
    await waitFor(() =>
      expect(screen.getByLabelText('Prezzo EUR')).toHaveValue('5.90'),
    )
    expect(screen.getByLabelText('Prezzo CHF')).toHaveValue('7.10')
  })

  it('addKeepsEditionAndReappliesProposal', async () => {
    const proposal = (volume: number) =>
      (replies['GET /purchase-lists/l1/proposal?editionId=e1'] = [
        200,
        { volume, priceEurCents: 590, releaseDate: null },
      ])
    proposal(21)
    replies['POST /purchase-lists/l1/items'] = [201, detail([item()])]
    await open(detail([]))
    const user = userEvent.setup()
    await pickBerserk(user)
    await waitFor(() => expect(screen.getByLabelText('Volume')).toHaveValue(21))
    proposal(22)
    await user.click(screen.getByRole('button', { name: 'Aggiungi' }))
    await waitFor(() => expect(screen.getByLabelText('Volume')).toHaveValue(22))
    await user.click(screen.getByRole('button', { name: 'Aggiungi' }))
    await waitFor(() =>
      expect(
        fetchMock.mock.calls
          .filter(
            ([u, i]) =>
              u === '/api/v1/purchase-lists/l1/items' && i.method === 'POST',
          )
          .map(([, i]) => JSON.parse(i.body).volume),
      ).toEqual([21, 22]),
    )
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
    await user.clear(screen.getByLabelText('Sconto (CHF)'))
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

  it('searchingASeriesDoesNotSubmitTheAddForm', async () => {
    await open(detail([]))
    const user = userEvent.setup()
    replies['GET /series?page=0&size=20&q=berserk'] = [
      200,
      {
        content: [{ id: 's1', title: 'Berserk', editionCount: 1 }],
        page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
      },
    ]
    await user.type(screen.getByLabelText('Cerca serie'), 'berserk')
    await user.click(screen.getByRole('button', { name: 'Cerca' }))
    expect(await screen.findByRole('button', { name: /Berserk/ })).toBeVisible()
    await user.type(screen.getByLabelText('Cerca serie'), '{Enter}')
    expect(screen.queryByText(/Scegli prima/)).toBeNull()
    expect(screen.queryByText('Importo non valido')).toBeNull()
    expect(screen.getByLabelText('Prezzo EUR')).not.toHaveAttribute(
      'aria-invalid',
    )
    expect(bodyOf('POST', '/purchase-lists/l1/items')).toBeUndefined()
  })

  it('proposalWithoutPriceClearsPrices', async () => {
    replies['GET /purchase-lists/l1/proposal?editionId=e1'] = [
      200,
      { volume: 2, priceEurCents: null, releaseDate: null },
    ]
    await open(detail([]))
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Prezzo EUR'), '5.90')
    expect(screen.getByLabelText('Prezzo CHF')).toHaveValue('7.10')
    await pickBerserk(user)
    await waitFor(() => expect(screen.getByLabelText('Volume')).toHaveValue(2))
    expect(screen.getByLabelText('Prezzo EUR')).toHaveValue('')
    expect(screen.getByLabelText('Prezzo CHF')).toHaveValue('')
  })

  it('editListHeader', async () => {
    replies['PATCH /purchase-lists/l1'] = [
      200,
      detail([], { description: 'Fumetti', month: 3, year: 2027 }),
    ]
    await open(detail([], { closed: true }))
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Modifica' }))
    await user.click(screen.getByRole('button', { name: 'Annulla' }))
    expect(screen.queryByLabelText('Descrizione')).toBeNull()
    expect(screen.getByRole('heading', { name: /Manga Ottobre/ })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Modifica' }))
    expect(screen.getByLabelText('Descrizione')).toHaveValue('Manga Ottobre')
    expect(screen.getByLabelText('Mese')).toHaveValue('2026-10')
    await user.clear(screen.getByLabelText('Descrizione'))
    await user.type(screen.getByLabelText('Descrizione'), 'Fumetti')
    fireEvent.change(screen.getByLabelText('Mese'), {
      target: { value: '2027-03' },
    })
    await user.click(screen.getByRole('button', { name: 'Salva' }))
    await waitFor(() =>
      expect(bodyOf('PATCH', '/purchase-lists/l1')).toEqual({
        description: 'Fumetti',
        month: 3,
        year: 2027,
      }),
    )
    expect(
      await screen.findByRole('heading', { name: /Fumetti · marzo 2027/ }),
    ).toBeVisible()
  })

  it('editListHeaderShowsFieldError', async () => {
    replies['PATCH /purchase-lists/l1'] = [
      400,
      {
        detail: 'Dati non validi',
        errors: [{ field: 'description', message: 'non deve essere vuoto' }],
      },
    ]
    await open(detail([]))
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Modifica' }))
    await user.click(screen.getByRole('button', { name: 'Salva' }))
    const msg = await screen.findByText('non deve essere vuoto')
    expect(screen.getByLabelText('Descrizione')).toHaveAccessibleDescription(
      msg.textContent!,
    )
  })

  it('discountEditorIsPrefilledAndCancelable', async () => {
    await open(detail([item()], { discountCents: 500, discountAuto: false }))
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Modifica sconto' }))
    expect(screen.getByLabelText('Sconto (CHF)')).toHaveValue('5.00')
    expect(screen.getByRole('button', { name: 'Torna al 10%' })).toBeVisible()
    fetchMock.mockClear()
    await user.click(screen.getByRole('button', { name: 'Annulla' }))
    expect(screen.queryByLabelText('Sconto (CHF)')).toBeNull()
    expect(bodyOf('PATCH', '/purchase-lists/l1')).toBeUndefined()
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
    await user.clear(screen.getByLabelText('Sconto (CHF)'))
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

describe('purchase list actions', () => {
  const two = [
    item({ id: 'a', seriesTitle: 'Alfa', volume: 1 }),
    item({ id: 'b', seriesTitle: 'Beta', volume: 2 }),
  ]
  const open = async (d: ListDetail) => {
    replies['GET /purchase-lists/l1'] = [200, d]
    replies['GET /purchase-lists'] = [
      200,
      [
        summary(),
        summary({ id: 'l2', description: 'Manga Novembre', month: 11 }),
      ],
    ]
    const view = renderApp('/purchases/l1')
    await screen.findByRole('heading', { name: /Manga Ottobre/ })
    return view
  }

  it('moveSelectedRows', async () => {
    replies['POST /purchase-lists/l1/items/move'] = [200, detail([])]
    await open(detail(two))
    const user = userEvent.setup()
    expect(
      screen.queryByRole('button', { name: 'Sposta selezionate' }),
    ).not.toBeInTheDocument()
    await user.click(screen.getByRole('checkbox', { name: /Alfa.*Vol\. 1/ }))
    await user.click(screen.getByRole('checkbox', { name: /Beta.*Vol\. 2/ }))
    await user.click(screen.getByRole('button', { name: 'Sposta selezionate' }))
    const dialog = screen.getByRole('dialog')
    const target = within(dialog).getByLabelText('Lista di destinazione')
    expect(
      within(target as HTMLElement).queryByText(/Manga Ottobre/),
    ).not.toBeInTheDocument()
    await user.selectOptions(target, 'l2')
    await user.type(
      within(dialog).getByLabelText('Data di uscita'),
      '2026-12-01',
    )
    await user.click(within(dialog).getByRole('button', { name: 'Sposta' }))
    await waitFor(() =>
      expect(bodyOf('POST', '/purchase-lists/l1/items/move')).toEqual({
        itemIds: ['a', 'b'],
        targetListId: 'l2',
        releaseDate: '2026-12-01',
      }),
    )
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    )
  })

  it('moveSingleRowKeepsDateWhenEmpty', async () => {
    replies['POST /purchase-lists/l1/items/move'] = [200, detail([])]
    await open(detail(two))
    const user = userEvent.setup()
    await user.click(
      within(rowOf('Beta')).getByRole('button', { name: 'Sposta' }),
    )
    const dialog = screen.getByRole('dialog')
    await user.selectOptions(
      within(dialog).getByLabelText('Lista di destinazione'),
      'l2',
    )
    await user.click(within(dialog).getByRole('button', { name: 'Sposta' }))
    await waitFor(() =>
      expect(bodyOf('POST', '/purchase-lists/l1/items/move')).toEqual({
        itemIds: ['b'],
        targetListId: 'l2',
      }),
    )
  })

  it('moveDialogClosesOnEscape', async () => {
    await open(detail(two))
    const user = userEvent.setup()
    await user.click(
      within(rowOf('Beta')).getByRole('button', { name: 'Sposta' }),
    )
    expect(screen.getByRole('dialog')).toBeVisible()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('closeWithUnbookedAsksConfirmation', async () => {
    replies['POST /purchase-lists/l1/close'] = [
      200,
      detail(two, { closed: true }),
    ]
    await open(detail(two))
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Chiudi lista' }))
    expect(
      screen.getByText(
        '2 righe non prenotate: non diventeranno possedute e restano nel totale. Chiudere comunque?',
      ),
    ).toBeVisible()
    expect(fetchMock.mock.calls.some(([u]) => u.endsWith('/close'))).toBe(false)
    await user.click(screen.getByRole('button', { name: 'Sì, chiudi' }))
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Riapri lista' }),
      ).toBeVisible(),
    )
  })

  it('closeWithAllBookedClosesDirectly', async () => {
    const booked = [item({ id: 'a', booked: true })]
    replies['POST /purchase-lists/l1/close'] = [
      200,
      detail(booked, { closed: true }),
    ]
    await open(detail(booked))
    await userEvent
      .setup()
      .click(screen.getByRole('button', { name: 'Chiudi lista' }))
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Riapri lista' }),
      ).toBeVisible(),
    )
  })

  it('reopenClosedList', async () => {
    replies['POST /purchase-lists/l1/reopen'] = [200, detail(two)]
    await open(detail(two, { closed: true }))
    await userEvent
      .setup()
      .click(screen.getByRole('button', { name: 'Riapri lista' }))
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Chiudi lista' }),
      ).toBeVisible(),
    )
  })

  it('deleteOnlyWhenOpen', async () => {
    const closed = await open(detail(two, { closed: true }))
    expect(
      screen.queryByRole('button', { name: 'Elimina lista' }),
    ).not.toBeInTheDocument()
    closed.unmount()
    replies['DELETE /purchase-lists/l1'] = [204]
    await open(detail(two))
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Elimina lista' }))
    await user.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    await waitFor(() =>
      expect(screen.getByTestId('where')).toHaveTextContent(/^\/purchases$/),
    )
  })

  it('closeInvalidatesCollection', async () => {
    replies['POST /purchase-lists/l1/close'] = [
      200,
      detail(two, { closed: true }),
    ]
    const { client } = await open(detail(two))
    const spy = vi.spyOn(client, 'invalidateQueries')
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Chiudi lista' }))
    await user.click(screen.getByRole('button', { name: 'Sì, chiudi' }))
    await waitFor(() => {
      const keys = spy.mock.calls.map(([f]) => (f?.queryKey as string[])[0])
      expect(keys).toEqual(
        expect.arrayContaining([
          'purchase-lists',
          'collection',
          'edition',
          'collection-entry',
        ]),
      )
    })
  })

  it('moveDialogHasNoA11yViolations', async () => {
    const { container } = await open(detail(two))
    await userEvent
      .setup()
      .click(within(rowOf('Beta')).getByRole('button', { name: 'Sposta' }))
    expect((await axe(container)).violations).toEqual([])
  })

  it('cancelKeepsSelection', async () => {
    await open(detail(two))
    const user = userEvent.setup()
    await user.click(screen.getByRole('checkbox', { name: /Alfa.*Vol\. 1/ }))
    await user.click(screen.getByRole('checkbox', { name: /Beta.*Vol\. 2/ }))
    await user.click(screen.getByRole('button', { name: 'Sposta selezionate' }))
    await user.click(screen.getByRole('button', { name: 'Annulla' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByText('2 selezionate')).toBeVisible()
    expect(screen.getAllByRole('checkbox', { checked: true })).toHaveLength(2)
  })

  it('moveDialogPreselectsFirstTarget', async () => {
    await open(detail(two))
    const user = userEvent.setup()
    await user.click(
      within(rowOf('Beta')).getByRole('button', { name: 'Sposta' }),
    )
    const dialog = screen.getByRole('dialog')
    await waitFor(() =>
      expect(
        within(dialog).getByLabelText('Lista di destinazione'),
      ).toHaveValue('l2'),
    )
    expect(within(dialog).getByRole('button', { name: 'Sposta' })).toBeEnabled()
  })

  it('closeQuestionIsSingularForOneRow', async () => {
    await open(detail([item({ id: 'a' })]))
    await userEvent
      .setup()
      .click(screen.getByRole('button', { name: 'Chiudi lista' }))
    expect(
      screen.getByText(
        '1 riga non prenotata: non diventerà posseduta e resta nel totale. Chiudere comunque?',
      ),
    ).toBeVisible()
  })
})
