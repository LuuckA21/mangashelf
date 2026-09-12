import { act, render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ApiError, metadata, type MangaSearchResult } from '../api/client'
import AniListSearch from './AniListSearch'

vi.mock('../api/client', async (original) => ({
  ...(await original<typeof import('../api/client')>()),
  metadata: { search: vi.fn(), importManga: vi.fn() },
}))

const result: MangaSearchResult = {
  anilistId: 42,
  titleRomaji: 'Berserk',
  titleEnglish: null,
  titleNative: null,
  authors: null,
  coverUrl: null,
  status: null,
  startYear: null,
  totalVolumes: null,
  alreadyInCatalogue: false,
  mangaId: null,
}

beforeEach(() => {
  vi.mocked(metadata.search).mockReset().mockResolvedValue([])
  vi.mocked(metadata.importManga).mockReset()
})
afterEach(() => vi.useRealTimers())

function mount() {
  render(
    <MemoryRouter>
      <AniListSearch onImported={vi.fn()} />
    </MemoryRouter>,
  )
}
async function search(title: string) {
  fireEvent.change(screen.getByRole('textbox'), { target: { value: title } })
  await act(async () =>
    fireEvent.click(screen.getByRole('button', { name: 'Cerca' })),
  )
}

it('distinguishes an AniList outage from empty results and removes old results', async () => {
  vi.mocked(metadata.search)
    .mockResolvedValueOnce([result])
    .mockRejectedValueOnce(new ApiError(502, 'anilist_unavailable'))
  mount()
  await search('Berserk')
  expect(screen.getByText('Berserk')).toBeInTheDocument()
  await search('One Piece')
  expect(screen.getByRole('alert')).toHaveTextContent(
    'AniList è temporaneamente non disponibile',
  )
  expect(screen.queryByText('Berserk')).not.toBeInTheDocument()
  expect(screen.queryByText(/Nessun risultato/)).not.toBeInTheDocument()
})

it('waits for the cooldown before a manual retry and sends no automatic requests', async () => {
  vi.useFakeTimers()
  vi.mocked(metadata.search).mockRejectedValueOnce(
    new ApiError(429, 'anilist_rate_limited', undefined, 2),
  )
  mount()
  await search('Berserk')
  expect(screen.getByRole('alert')).toHaveTextContent('limite di richieste')
  expect(screen.getByRole('button', { name: 'Cerca' })).toBeDisabled()
  await act(async () => vi.advanceTimersByTime(2000))
  expect(screen.getByRole('button', { name: 'Cerca' })).toBeEnabled()
  expect(metadata.search).toHaveBeenCalledTimes(1)
  await search('Berserk')
  expect(screen.getByText(/Nessun risultato/)).toBeInTheDocument()
})

it('retains import results on failure and shows the provider error', async () => {
  vi.mocked(metadata.search).mockResolvedValue([result])
  vi.mocked(metadata.importManga).mockRejectedValue(
    new ApiError(502, 'anilist_unavailable', undefined, 30),
  )
  mount()
  await search('Berserk')
  await act(async () =>
    fireEvent.click(screen.getByRole('button', { name: 'Importa' })),
  )
  expect(screen.getByText('Berserk')).toBeInTheDocument()
  expect(screen.getByRole('alert')).toHaveTextContent(
    'AniList è temporaneamente non disponibile',
  )
  expect(screen.getByRole('button', { name: 'Importa' })).toBeDisabled()
})

it('keeps the submitted title attached to an empty result while editing', async () => {
  mount()
  await search('Berserk')
  fireEvent.change(screen.getByRole('textbox'), {
    target: { value: 'One Piece' },
  })
  expect(screen.getByText(/Nessun risultato/)).toHaveTextContent('Berserk')
})

it('distinguishes network failure from provider failure', async () => {
  vi.mocked(metadata.search).mockRejectedValue(new TypeError('Failed to fetch'))
  mount()
  await search('Berserk')
  expect(screen.getByRole('alert')).toHaveTextContent(
    'Server non raggiungibile',
  )
})
