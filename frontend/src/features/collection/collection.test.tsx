import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../../App'
import type {
  CollectionEntry,
  CollectionStats as Stats,
  EditionResponse,
} from '../../api/types'
import { CollectionStats } from './CollectionStats'
import { axe } from '../../test/axe'

const fetchMock = vi.fn()
type Reply = [status: number, body?: unknown]
// A function reply is evaluated per request, so a test can change or delay the answer.
let replies: Record<string, Reply | (() => Reply | Promise<Reply>)> = {}

const entry = (over: Partial<CollectionEntry> = {}): CollectionEntry => ({
  editionId: 'e1',
  seriesId: 's1',
  seriesTitle: 'Berserk',
  editionName: 'Deluxe',
  publisher: 'Panini',
  type: 'REGULAR',
  status: 'ONGOING',
  volumeCount: 5,
  hasCover: false,
  version: 1,
  owned: [],
  ...over,
})
const edition = (over: Partial<EditionResponse> = {}): EditionResponse => ({
  id: 'e1',
  seriesId: 's1',
  seriesTitle: 'Berserk',
  name: 'Deluxe',
  publisher: 'Panini',
  type: 'REGULAR',
  status: 'ONGOING',
  volumeCount: 5,
  hasCover: false,
  canEditCover: false,
  version: 3,
  collectionCount: 1,
  inMyCollection: true,
  ...over,
})
const stats = (over: Partial<Stats> = {}): Stats => ({
  editions: 0,
  series: 0,
  ownedVolumes: 0,
  totalVolumes: 0,
  completedEditions: 0,
  byPublisher: [],
  byType: [],
  byStatus: [],
  ...over,
})
const pageOf = (content: CollectionEntry[]) => ({
  content,
  page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 },
})

beforeEach(() => {
  replies = {
    'GET /auth/me': [
      200,
      { id: '1', username: 'anna', role: 'USER', mustChangePassword: false },
    ],
    'GET /collection/stats': [200, stats()],
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

function renderApp(path: string) {
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

const listUrl = (qs: string) => `GET /collection?page=0&size=20${qs}`

describe('collection page', () => {
  it('showsCoverThumbnailWithVersion', async () => {
    replies[listUrl('')] = [
      200,
      pageOf([entry({ hasCover: true, version: 7 })]),
    ]
    const { container } = renderApp('/')
    await screen.findByText('Deluxe')
    expect(container.querySelector('img.thumb')).toHaveAttribute(
      'src',
      '/api/v1/editions/e1/cover?v=7',
    )
  })

  it('groupsEditionsBySeriesWithProgress', async () => {
    replies[listUrl('')] = [
      200,
      pageOf([
        entry({
          volumeCount: 114,
          owned: Array.from({ length: 110 }, (_, i) => i + 1),
        }),
        entry({
          editionId: 'e2',
          editionName: 'Maximum',
          volumeCount: 3,
          owned: [1],
        }),
        entry({
          editionId: 'e3',
          seriesId: 's2',
          seriesTitle: 'Vinland',
          editionName: 'Base',
        }),
      ]),
    ]
    const { container } = renderApp('/')
    const berserk = (
      await screen.findByRole('heading', { name: 'Berserk' })
    ).closest('section')!
    expect(within(berserk).getByText('110/114')).toBeInTheDocument()
    expect(within(berserk).getByText('Maximum')).toBeInTheDocument()
    expect(within(berserk).getByText('Mancano: 2–3')).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(2)
    expect((await axe(container)).violations).toEqual([])
  })

  it('wishEditionShowsZeroOwned', async () => {
    replies[listUrl('')] = [200, pageOf([entry({ volumeCount: 1, owned: [] })])]
    renderApp('/')
    expect(await screen.findByText('0/1')).toBeInTheDocument()
  })

  it('incompleteChipFilters', async () => {
    replies[listUrl('')] = [200, pageOf([entry()])]
    replies[listUrl('&incomplete=true')] = [200, pageOf([])]
    renderApp('/')
    await screen.findByText('Deluxe')
    await userEvent.click(screen.getByRole('button', { name: 'Incomplete' }))
    expect(
      await screen.findByText('Nessuna edizione in collezione.'),
    ).toBeInTheDocument()
    expect(fetchMock.mock.calls.map((c) => c[0])).toContain(
      '/api/v1/collection?page=0&size=20&incomplete=true',
    )
    expect(screen.getByRole('button', { name: 'Incomplete' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })

  it('filtersComeFromTheUrl', async () => {
    replies[listUrl('&q=ber&type=VARIANT&status=COMPLETED')] = [
      200,
      pageOf([entry()]),
    ]
    renderApp('/?q=ber&type=VARIANT&status=COMPLETED&page=1')
    expect(await screen.findByText('Deluxe')).toBeInTheDocument()
    expect(screen.getByLabelText('Cerca per serie o edizione')).toHaveValue(
      'ber',
    )
  })

  it('invalidPageParamFallsBackToFirstPage', async () => {
    replies[listUrl('')] = [200, pageOf([entry()])]
    renderApp('/?page=2.5')
    expect(await screen.findByText('Deluxe')).toBeInTheDocument()
  })

  it('pagesWithPrevNext', async () => {
    replies[listUrl('')] = [
      200,
      {
        content: [entry()],
        page: { size: 20, number: 0, totalElements: 30, totalPages: 2 },
      },
    ]
    replies['GET /collection?page=1&size=20'] = [
      200,
      {
        content: [entry({ editionName: 'Seconda' })],
        page: { size: 20, number: 1, totalElements: 30, totalPages: 2 },
      },
    ]
    renderApp('/')
    expect(await screen.findByText('Pagina 1 di 2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Precedente' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Successiva' }))
    expect(await screen.findByText('Seconda')).toBeInTheDocument()
    expect(screen.getByText('Pagina 2 di 2')).toBeInTheDocument()
  })
})

describe('edition page', () => {
  it('showsOwnedVolumesAndMissingSummary', async () => {
    replies['GET /editions/e1'] = [200, edition({ hasCover: true })]
    replies['GET /collection/editions/e1'] = [200, entry({ owned: [1, 2, 4] })]
    const { container } = renderApp('/editions/e1')
    expect(
      await screen.findByRole('button', { name: 'Volume 2, posseduto' }),
    ).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('Mancano: 3, 5')).toBeInTheDocument()
    expect(
      screen.getByRole('img', { name: 'Copertina di Berserk Deluxe' }),
    ).toHaveAttribute('src', '/api/v1/editions/e1/cover?v=3')
    expect((await axe(container)).violations).toEqual([])
  })

  const tile = (n: number, owned: boolean) =>
    screen.findByRole('button', {
      name: `Volume ${n}, ${owned ? 'posseduto' : 'mancante'}`,
    })
  const never = () => new Promise<Reply>(() => {})
  const gate = () => {
    let open!: () => void
    const opened = new Promise<void>((r) => (open = r))
    return { open, opened }
  }

  it('failedSaveRollsBackTileAndShowsError', async () => {
    const put = gate()
    replies['GET /editions/e1'] = [200, edition()]
    replies['PUT /collection/editions/e1/volumes'] = async () => {
      await put.opened
      return [500, { detail: 'boom' }]
    }
    let gets = 0
    // After the first load the refetch never answers, so only the rollback can restore the tile.
    replies['GET /collection/editions/e1'] = () =>
      gets++ === 0 ? [200, entry({ owned: [1] })] : never()
    renderApp('/editions/e1')
    await userEvent.click(await tile(3, false))
    expect(await tile(3, true)).toHaveAttribute('aria-pressed', 'true')
    put.open()
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveClass('error')
    expect(await tile(3, false)).toHaveAttribute('aria-pressed', 'false')
  })

  it('rapidClicksFirstFailsSecondSucceedsEndsOnSecondResponse', async () => {
    const puts: string[] = []
    const first = gate()
    let server = [1]
    replies['GET /editions/e1'] = [200, edition()]
    replies['GET /collection/editions/e1'] = () => [
      200,
      entry({ owned: server }),
    ]
    replies['PUT /collection/editions/e1/volumes'] = async () => {
      if (puts.push('put') === 1) {
        await first.opened
        return [500, { detail: 'boom' }]
      }
      server = [1, 4]
      return [200, entry({ owned: server })]
    }
    renderApp('/editions/e1')
    await userEvent.click(await tile(3, false))
    await userEvent.click(await tile(4, false))
    // The second PUT must wait for the first (mutation scope).
    await new Promise((r) => setTimeout(r, 20))
    expect(puts).toHaveLength(1)
    first.open()
    expect(await screen.findByRole('alert')).toHaveClass('error')
    await vi.waitFor(() => expect(puts).toHaveLength(2))
    await tile(4, true)
    await vi.waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Volume 3, mancante' }),
      ).toBeInTheDocument(),
    )
    expect(screen.getByRole('alert')).toBeInTheDocument()
    const bodies = fetchMock.mock.calls
      .filter((c) => c[1].method === 'PUT')
      .map((c) => c[1].body)
    expect(bodies).toEqual(['{"owned":[1,3]}', '{"owned":[1,3,4]}'])
  })

  it('rapidClicksBothFailRestoreServerState', async () => {
    replies['GET /editions/e1'] = [200, edition()]
    let gets = 0
    replies['GET /collection/editions/e1'] = () =>
      gets++ === 0 ? [200, entry({ owned: [1] })] : never()
    replies['PUT /collection/editions/e1/volumes'] = [500, { detail: 'boom' }]
    renderApp('/editions/e1')
    await userEvent.click(await tile(3, false))
    await userEvent.click(await tile(4, false))
    await vi.waitFor(() =>
      expect(
        fetchMock.mock.calls.filter((c) => c[1].method === 'PUT'),
      ).toHaveLength(2),
    )
    await vi.waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Volume 3, mancante' }),
      ).toBeInTheDocument()
      expect(
        screen.getByRole('button', { name: 'Volume 4, mancante' }),
      ).toBeInTheDocument()
    })
    expect(
      screen.getByRole('button', { name: 'Volume 1, posseduto' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveClass('error')
  })

  it('firstToggleOnEditionOutsideCollectionSavesFullSet', async () => {
    let saved = false
    replies['GET /editions/e1'] = () => [
      200,
      edition({ inMyCollection: saved }),
    ]
    replies['PUT /collection/editions/e1/volumes'] = () => {
      saved = true
      return [200, entry({ owned: [2] })]
    }
    replies['GET /collection/editions/e1'] = [200, entry({ owned: [2] })]
    renderApp('/editions/e1')
    await userEvent.click(await tile(2, false))
    expect(await tile(2, true)).toHaveAttribute('aria-pressed', 'true')
    expect(
      await screen.findByRole('button', { name: 'Togli dalla collezione' }),
    ).toBeInTheDocument()
    const put = fetchMock.mock.calls.find((c) => c[1].method === 'PUT')!
    expect(put[1].body).toBe('{"owned":[2]}')
  })

  it('removeAsksConfirmationThenDeletesAndShowsEmptyShelf', async () => {
    let removed = false
    replies['GET /editions/e1'] = () => [
      200,
      edition({ inMyCollection: !removed }),
    ]
    replies['GET /collection/editions/e1'] = () =>
      removed ? [404, { detail: 'no' }] : [200, entry({ owned: [1] })]
    replies['DELETE /collection/editions/e1'] = () => {
      removed = true
      return [204]
    }
    renderApp('/editions/e1')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Togli dalla collezione' }),
    )
    expect(fetchMock.mock.calls.some((c) => c[1].method === 'DELETE')).toBe(
      false,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    expect(
      await screen.findByRole('button', {
        name: 'Aggiungi alla mia collezione',
      }),
    ).toBeInTheDocument()
    expect(await tile(1, false)).toHaveAttribute('aria-pressed', 'false')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

describe('collection stats', () => {
  const panel = () => screen.findByRole('region', { name: 'Statistiche' })
  const value = (region: HTMLElement, label: string) =>
    within(region).getByText(label).nextElementSibling

  it('showsNumbersMissingAndRoundedDownPercent', async () => {
    replies[listUrl('')] = [200, pageOf([entry()])]
    replies['GET /collection/stats'] = [
      200,
      stats({
        editions: 14,
        series: 9,
        ownedVolumes: 411,
        totalVolumes: 412,
        completedEditions: 5,
      }),
    ]
    renderApp('/')
    const region = await panel()
    expect(value(region, 'Volumi')).toHaveTextContent('411')
    expect(value(region, 'Edizioni')).toHaveTextContent('14')
    expect(value(region, 'Serie')).toHaveTextContent('9')
    expect(value(region, 'Completate')).toHaveTextContent('5')
    expect(value(region, 'Mancanti')).toHaveTextContent('1')
    expect(value(region, 'Completamento')).toHaveTextContent('99%')
  })

  it('rendersBarsAsText', async () => {
    replies[listUrl('')] = [200, pageOf([entry()])]
    replies['GET /collection/stats'] = [
      200,
      stats({
        editions: 3,
        ownedVolumes: 240,
        totalVolumes: 300,
        byPublisher: [
          { key: 'Star Comics', volumes: 210 },
          { key: null, volumes: 30 },
        ],
        byType: [{ key: 'REGULAR', volumes: 240 }],
        byStatus: [{ key: 'COMPLETED', volumes: 240 }],
      }),
    ]
    renderApp('/')
    const region = await panel()
    for (const text of [
      'Star Comics: 210 volumi',
      'Altri: 30 volumi',
      'Regolare: 240 volumi',
      'Concluse: 240 volumi',
    ])
      expect(within(region).getByText(text)).toBeInTheDocument()
    expect(
      within(region).getByRole('heading', { name: 'Per editore' }),
    ).toBeInTheDocument()
  })

  it('singleVolumeUsesSingular', async () => {
    replies[listUrl('')] = [200, pageOf([entry()])]
    replies['GET /collection/stats'] = [
      200,
      stats({
        editions: 1,
        ownedVolumes: 1,
        totalVolumes: 5,
        byPublisher: [{ key: null, volumes: 1 }],
      }),
    ]
    renderApp('/')
    expect(
      within(await panel()).getByText('Altri: 1 volume'),
    ).toBeInTheDocument()
  })

  it('hiddenWhenCollectionIsEmpty', async () => {
    replies[listUrl('')] = [200, pageOf([])]
    renderApp('/')
    await screen.findByText('Nessuna edizione in collezione.')
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/collection/stats',
        expect.anything(),
      ),
    )
    expect(
      screen.queryByRole('region', { name: 'Statistiche' }),
    ).not.toBeInTheDocument()
  })

  it('statsErrorKeepsTheList', async () => {
    replies[listUrl('')] = [200, pageOf([entry()])]
    replies['GET /collection/stats'] = [500, { detail: 'boom' }]
    renderApp('/')
    expect(await screen.findByText('Deluxe')).toBeInTheDocument()
    const message = await screen.findByText('Statistiche non disponibili')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    replies['GET /collection/stats'] = [
      200,
      stats({ editions: 1, ownedVolumes: 2, totalVolumes: 5 }),
    ]
    await userEvent.click(
      within(message.closest('section')!).getByRole('button', {
        name: 'Riprova',
      }),
    )
    expect(value(await panel(), 'Volumi')).toHaveTextContent('2')
  })

  it('refreshesAfterTickingAVolume', async () => {
    replies['GET /collection/stats'] = [
      200,
      stats({ editions: 1, ownedVolumes: 3, totalVolumes: 5 }),
    ]
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    render(
      <QueryClientProvider client={client}>
        <CollectionStats />
      </QueryClientProvider>,
    )
    expect(value(await panel(), 'Volumi')).toHaveTextContent('3')
    replies['GET /collection/stats'] = [
      200,
      stats({ editions: 1, ownedVolumes: 4, totalVolumes: 5 }),
    ]
    await client.invalidateQueries({ queryKey: ['collection'] })
    await waitFor(async () =>
      expect(value(await panel(), 'Volumi')).toHaveTextContent('4'),
    )
  })

  it('tickingAVolumeSavesWhileStatsAreCached', async () => {
    replies[listUrl('')] = [200, pageOf([entry({ owned: [1] })])]
    replies['GET /collection/stats'] = [
      200,
      stats({ editions: 1, ownedVolumes: 1, totalVolumes: 5 }),
    ]
    replies['GET /editions/e1'] = [200, edition()]
    replies['GET /collection/editions/e1'] = [200, entry({ owned: [1] })]
    replies['PUT /collection/editions/e1/volumes'] = [
      200,
      entry({ owned: [1, 3] }),
    ]
    renderApp('/')
    await panel()
    await userEvent.click(screen.getByRole('link', { name: 'Deluxe' }))
    const tile3 = await screen.findByRole('button', {
      name: 'Volume 3, mancante',
    })
    // What the server returns once the save went through.
    replies['GET /collection/editions/e1'] = [200, entry({ owned: [1, 3] })]
    await userEvent.click(tile3)
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/collection/editions/e1/volumes',
        expect.objectContaining({ method: 'PUT' }),
      ),
    )

    expect(
      await screen.findByRole('button', { name: 'Volume 3, posseduto' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('percentIsExactForValuesFloatingPointGetsWrong', async () => {
    replies[listUrl('')] = [200, pageOf([entry()])]
    replies['GET /collection/stats'] = [
      200,
      stats({ editions: 1, ownedVolumes: 29, totalVolumes: 100 }),
    ]
    renderApp('/')
    expect(value(await panel(), 'Completamento')).toHaveTextContent('29%')
  })

  it('statsPanelIsKeyboardScrollable', async () => {
    replies[listUrl('')] = [200, pageOf([entry()])]
    replies['GET /collection/stats'] = [
      200,
      stats({ editions: 1, ownedVolumes: 1, totalVolumes: 5 }),
    ]
    renderApp('/')
    // It scrolls inside itself on short windows: keyboard users must reach it.
    expect(await panel()).toHaveAttribute('tabindex', '0')
  })

  it('statsPanelHasNoAxeViolations', async () => {
    replies[listUrl('')] = [200, pageOf([entry()])]
    replies['GET /collection/stats'] = [
      200,
      stats({
        editions: 2,
        series: 1,
        ownedVolumes: 5,
        totalVolumes: 8,
        byPublisher: [{ key: 'Panini', volumes: 5 }],
        byType: [{ key: 'VARIANT', volumes: 5 }],
        byStatus: [{ key: 'ONGOING', volumes: 5 }],
      }),
    ]
    const { container } = renderApp('/')
    await panel()
    expect((await axe(container)).violations).toEqual([])
  })
})
