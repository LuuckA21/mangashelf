import { api } from './http'
import type { Manga, MangaSearchResult } from './types'

export const metadata = {
  search: (q: string) =>
    api.get<MangaSearchResult[]>(
      `/api/metadata/search?q=${encodeURIComponent(q)}`,
    ),
  importManga: (anilistId: number) =>
    api.post<Manga>(`/api/metadata/import/${anilistId}`),
}
