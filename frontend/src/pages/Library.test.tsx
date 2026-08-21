import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { catalog, type Manga, type PageResult } from '../api/client'
import Library from './Library'

vi.mock('../api/client', () => ({
  catalog: { listManga: vi.fn() },
}))

vi.mock('../api/session', () => ({
  useSession: () => ({
    user: { id: 1, username: 'admin', email: 'admin@test', role: 'ADMIN' },
    loading: false,
    setUser: vi.fn(),
  }),
}))

vi.mock('../components/Layout', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

vi.mock('../components/AniListSearch', () => ({ default: () => null }))
vi.mock('../components/MangaForm', () => ({ default: () => null }))

const listManga = vi.mocked(catalog.listManga)

function manga(id: number, title: string): Manga {
  return {
    id,
    titleRomaji: title,
    titleNative: null,
    titleEnglish: null,
    displayTitle: title,
    authors: null,
    description: null,
    coverUrl: null,
    status: null,
    genres: null,
    startYear: null,
    totalVolumes: null,
    anilistId: null,
  }
}

function page(content: Manga[], number: number, totalPages: number,
              totalElements = content.length): PageResult<Manga> {
  return { content, size: 24, number, totalPages, totalElements }
}

function renderLibrary() {
  render(<MemoryRouter><Library /></MemoryRouter>)
}

describe('Library', () => {
  beforeEach(() => listManga.mockReset())

  it('appends every page when loading beyond the first 24 manga', async () => {
    listManga
      .mockResolvedValueOnce(page([manga(1, 'First page')], 0, 2, 25))
      .mockResolvedValueOnce(page([manga(25, 'Beyond twenty-four')], 1, 2, 25))

    renderLibrary()
    expect(await screen.findByText('First page')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Carica altri' }))

    expect(await screen.findByText('Beyond twenty-four')).toBeInTheDocument()
    expect(screen.getByText('First page')).toBeInTheDocument()
    expect(listManga).toHaveBeenLastCalledWith('', 1)
    expect(screen.queryByRole('button', { name: 'Carica altri' })).not.toBeInTheDocument()
  })

  it('searches on the server and replaces the previous result set', async () => {
    listManga
      .mockResolvedValueOnce(page([manga(1, 'Berserk')], 0, 1))
      .mockResolvedValueOnce(page([manga(2, 'Vagabond')], 0, 1))
    const user = userEvent.setup()

    renderLibrary()
    expect(await screen.findByText('Berserk')).toBeInTheDocument()

    await user.type(screen.getByRole('textbox', { name: 'Cerca nel catalogo' }),
      '  Vagabond  ')
    await user.click(screen.getByRole('button', { name: 'Cerca' }))

    expect(await screen.findByText('Vagabond')).toBeInTheDocument()
    expect(screen.queryByText('Berserk')).not.toBeInTheDocument()
    await waitFor(() => expect(listManga).toHaveBeenLastCalledWith('  Vagabond  ', 0))
  })

  it('keeps the admin actions in their own responsive control group', async () => {
    listManga.mockResolvedValue(page([], 0, 0))
    renderLibrary()

    await screen.findByRole('button', { name: 'Importa da AniList' })
    expect(screen.getByRole('button', { name: 'Importa da AniList' }).parentElement)
      .toHaveClass('catalog-actions')
    expect(screen.getByRole('button', { name: 'Cerca' }).closest('form'))
      .toHaveClass('catalog-search')
  })
})
