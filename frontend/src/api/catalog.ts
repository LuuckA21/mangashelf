import { api, ApiError, readCookie } from './http'
import type { Manga, MangaSearchResult, PageResult, Series } from './types'

interface SpringPage<T> {
  content: T[]
  page?: { size: number; number: number; totalElements: number; totalPages: number }
}

function pageResult<T>(response: SpringPage<T>): PageResult<T> {
  const content = response.content ?? []
  const metadata = response.page ?? {
    size: content.length,
    number: 0,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
  }
  return { content, ...metadata }
}

export const catalog = {
  listManga: (q = '', page = 0, size = 24) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    const query = q.trim()
    if (query) params.set('q', query)
    return api.get<SpringPage<Manga>>(`/api/manga?${params}`).then(pageResult)
  },
  getManga: (id: number) => api.get<Manga>(`/api/manga/${id}`),
  createManga: (body: Partial<Manga>) => api.post<Manga>('/api/manga', body),
  updateManga: (id: number, body: Partial<Manga>) =>
    api.put<Manga>(`/api/manga/${id}`, body),
  deleteManga: (id: number) => api.delete<void>(`/api/manga/${id}`),

  uploadCover: async (id: number, file: File): Promise<Manga> => {
    const body = new FormData()
    body.append('file', file)
    const token = readCookie('XSRF-TOKEN')
    const response = await fetch(`/api/manga/${id}/cover`, {
      method: 'POST',
      headers: token ? { 'X-XSRF-TOKEN': token } : {},
      credentials: 'same-origin',
      body,
    })

    const payload = await response.json().catch(() => null)
    if (!response.ok) {
      throw new ApiError(response.status, payload?.error ?? 'unknown_error')
    }
    return payload as Manga
  },

  listSeries: (mangaId: number) => api.get<Series[]>(`/api/manga/${mangaId}/series`),
  getSeries: (id: number) => api.get<Series>(`/api/series/${id}`),
  createSeries: (mangaId: number, body: Partial<Series>) =>
    api.post<Series>(`/api/manga/${mangaId}/series`, body),
  updateSeries: (id: number, body: Partial<Series>) =>
    api.put<Series>(`/api/series/${id}`, body),
  deleteSeries: (id: number) => api.delete<void>(`/api/series/${id}`),
}

export const metadata = {
  search: (q: string) =>
    api.get<MangaSearchResult[]>(`/api/metadata/search?q=${encodeURIComponent(q)}`),
  importManga: (anilistId: number) =>
    api.post<Manga>(`/api/metadata/import/${anilistId}`),
}
