import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../../App'
import type { CollectionEntry, EditionResponse } from '../../api/types'
import { axe } from '../../test/axe'

const fetchMock = vi.fn()
type Reply = [status: number, body?: unknown]
let replies: Record<string, Reply> = {}

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
  }
  fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
    const [status, body] = replies[
      `${init.method} ${url.replace('/api/v1', '')}`
    ] ?? [404, { detail: `${init.method} ${url}` }]
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
    await userEvent.click(
      screen.getByRole('button', { name: 'Solo incomplete' }),
    )
    expect(
      await screen.findByText('Nessuna edizione in collezione.'),
    ).toBeInTheDocument()
    expect(fetchMock.mock.calls.map((c) => c[0])).toContain(
      '/api/v1/collection?page=0&size=20&incomplete=true',
    )
    expect(
      screen.getByRole('button', { name: 'Solo incomplete' }),
    ).toHaveAttribute('aria-pressed', 'true')
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

  it('failedSaveRollsBackTileAndShowsError', async () => {
    replies['GET /editions/e1'] = [200, edition()]
    replies['GET /collection/editions/e1'] = [200, entry({ owned: [1] })]
    replies['PUT /collection/editions/e1/volumes'] = [500, { detail: 'boom' }]
    renderApp('/editions/e1')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Volume 3, mancante' }),
    )
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveClass('error')
    expect(
      screen.getByRole('button', { name: 'Volume 3, mancante' }),
    ).toHaveAttribute('aria-pressed', 'false')
  })

  it('firstToggleOnEditionOutsideCollectionSavesFullSet', async () => {
    replies['GET /editions/e1'] = [200, edition({ inMyCollection: false })]
    replies['PUT /collection/editions/e1/volumes'] = [
      200,
      entry({ owned: [2] }),
    ]
    renderApp('/editions/e1')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Volume 2, mancante' }),
    )
    expect(
      await screen.findByRole('button', { name: 'Volume 2, posseduto' }),
    ).toHaveAttribute('aria-pressed', 'true')
    const put = fetchMock.mock.calls.find((c) => c[1].method === 'PUT')!
    expect(put[1].body).toBe('{"owned":[2]}')
    expect(fetchMock.mock.calls.map((c) => c[0])).not.toContain(
      '/api/v1/collection/editions/e1',
    )
  })

  it('removeAsksConfirmationThenDeletes', async () => {
    replies['GET /editions/e1'] = [200, edition()]
    replies['GET /collection/editions/e1'] = [200, entry({ owned: [1] })]
    replies['DELETE /collection/editions/e1'] = [204]
    renderApp('/editions/e1')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Togli dalla collezione' }),
    )
    expect(fetchMock.mock.calls.some((c) => c[1].method === 'DELETE')).toBe(
      false,
    )
    replies['GET /editions/e1'] = [200, edition({ inMyCollection: false })]
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    await vi.waitFor(() =>
      expect(fetchMock.mock.calls.some((c) => c[1].method === 'DELETE')).toBe(
        true,
      ),
    )
    await screen.findByRole('button', { name: 'Aggiungi alla mia collezione' })
  })
})
