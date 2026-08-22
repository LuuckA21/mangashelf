import { api } from './http'
import type { EditionSummary, OwnedVolume, SeriesProgress } from './types'

export const collection = {
  listOwned: () => api.get<OwnedVolume[]>('/api/collection/volumes'),
  summary: () => api.get<EditionSummary[]>('/api/collection/summary'),
  progress: (seriesId: number) =>
    api.get<SeriesProgress>(`/api/collection/series/${seriesId}`),
  add: (seriesId: number, number: number) =>
    api.post<void>(`/api/collection/series/${seriesId}/volumes/${number}`),
  remove: (seriesId: number, number: number) =>
    api.delete<void>(`/api/collection/series/${seriesId}/volumes/${number}`),
  addRange: (seriesId: number, from: number, to: number) =>
    api.post<{ added: number }>(`/api/collection/series/${seriesId}/range?from=${from}&to=${to}`),
}
