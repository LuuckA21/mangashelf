import { ApiError, api, csrfToken } from './http'
import { pageResult, type Manga, type Series, type SpringPage } from './types'

export const catalog = {
  listManga: (q = '', page = 0, size = 24) => {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
    })
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
    const token = csrfToken()
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

  listSeries: (mangaId: number) =>
    api.get<Series[]>(`/api/manga/${mangaId}/series`),
  getSeries: (id: number) => api.get<Series>(`/api/series/${id}`),
  createSeries: (mangaId: number, body: Partial<Series>) =>
    api.post<Series>(`/api/manga/${mangaId}/series`, body),
  updateSeries: (id: number, body: Partial<Series>) =>
    api.put<Series>(`/api/series/${id}`, body),
  deleteSeries: (id: number) => api.delete<void>(`/api/series/${id}`),
}
