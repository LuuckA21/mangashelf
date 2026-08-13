/**
 * Thin fetch wrapper that carries the session cookie and the CSRF token.
 *
 * Spring Security hands out the token in a readable XSRF-TOKEN cookie and
 * expects it back in the X-XSRF-TOKEN header on any state-changing request.
 * Forgetting the header is the single most common cause of a 403 that looks
 * like a permissions bug but is not one.
 */

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly fields?: Record<string, string>,
  ) {
    super(code)
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)

  if (init.body) headers.set('Content-Type', 'application/json')

  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = readCookie('XSRF-TOKEN')
    if (token) headers.set('X-XSRF-TOKEN', token)
  }

  const response = await fetch(path, {
    ...init,
    headers,
    // Without this the browser omits the session cookie and every call
    // after login comes back 401.
    credentials: 'same-origin',
  })

  if (response.status === 204) return undefined as T

  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    throw new ApiError(
      response.status,
      payload?.error ?? 'unknown_error',
      payload?.fields,
    )
  }

  return payload as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: body ? JSON.stringify(body) : undefined }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}

// ------------------------------------------------------------------ types

export interface User {
  id: number
  username: string
  email: string
  role: 'USER' | 'ADMIN'
}

export type PublicationStatus =
  | 'FINISHED' | 'RELEASING' | 'NOT_YET_RELEASED' | 'CANCELLED' | 'HIATUS'

export interface Manga {
  id: number
  titleRomaji: string
  titleNative: string | null
  titleEnglish: string | null
  displayTitle: string
  authors: string | null
  description: string | null
  coverUrl: string | null
  status: PublicationStatus | null
  genres: string[] | null
  startYear: number | null
  totalVolumes: number | null
  anilistId: number | null
}

export interface Series {
  id: number
  mangaId: number
  mangaTitle: string
  publisher: string
  language: string
  name: string
  totalVolumes: number | null
  completed: boolean
  volumeCount: number
}

export interface Volume {
  id: number
  seriesId: number
  number: number
  title: string | null
  isbn13: string | null
  releaseDate: string | null
  coverUrl: string | null
  upcoming: boolean
}

export interface SeriesProgress {
  seriesId: number
  seriesName: string
  mangaTitle: string
  totalVolumes: number
  ownedCount: number
  ownedNumbers: number[]
  missingNumbers: number[]
}

export interface OwnedVolume {
  volumeId: number
  number: number
  volumeTitle: string | null
  seriesId: number
  seriesName: string
  publisher: string
  mangaId: number
  mangaTitle: string
  addedAt: string
}

/** Spring serialises Page as an object with a content array. */
interface Page<T> { content: T[]; totalElements: number; totalPages: number }

// --------------------------------------------------------------- endpoints

export const auth = {
  me: () => api.get<User>('/api/auth/me'),
  login: (login: string, password: string) =>
    api.post<User>('/api/auth/login', { login, password }),
  register: (username: string, email: string, password: string) =>
    api.post<User>('/api/auth/register', { username, email, password }),
  logout: () => api.post<void>('/api/auth/logout'),
}

export const catalog = {
  listManga: (q?: string) =>
    api.get<Page<Manga>>(`/api/manga${q ? `?q=${encodeURIComponent(q)}` : ''}`)
      .then((page) => page.content ?? []),
  getManga: (id: number) => api.get<Manga>(`/api/manga/${id}`),
  createManga: (body: Partial<Manga>) => api.post<Manga>('/api/manga', body),
  updateManga: (id: number, body: Partial<Manga>) =>
    api.put<Manga>(`/api/manga/${id}`, body),
  deleteManga: (id: number) => api.delete<void>(`/api/manga/${id}`),

  listSeries: (mangaId: number) => api.get<Series[]>(`/api/manga/${mangaId}/series`),
  getSeries: (id: number) => api.get<Series>(`/api/series/${id}`),
  createSeries: (mangaId: number, body: Partial<Series>) =>
    api.post<Series>(`/api/manga/${mangaId}/series`, body),
  updateSeries: (id: number, body: Partial<Series>) =>
    api.put<Series>(`/api/series/${id}`, body),
  deleteSeries: (id: number) => api.delete<void>(`/api/series/${id}`),

  listVolumes: (seriesId: number) => api.get<Volume[]>(`/api/series/${seriesId}/volumes`),
  createVolumes: (seriesId: number, from: number, to: number) =>
    api.post<Volume[]>(`/api/series/${seriesId}/volumes/bulk`, { from, to }),
  deleteVolume: (id: number) => api.delete<void>(`/api/volumes/${id}`),
}

export interface MangaSearchResult {
  anilistId: number
  titleRomaji: string
  titleEnglish: string | null
  titleNative: string | null
  authors: string | null
  coverUrl: string | null
  status: string | null
  startYear: number | null
  totalVolumes: number | null
  alreadyInCatalogue: boolean
  mangaId: number | null
}

export const metadata = {
  search: (q: string) =>
    api.get<MangaSearchResult[]>(`/api/metadata/search?q=${encodeURIComponent(q)}`),
  importManga: (anilistId: number) =>
    api.post<Manga>(`/api/metadata/import/${anilistId}`),
}

export const collection = {
  listOwned: () => api.get<OwnedVolume[]>('/api/collection/volumes'),
  progress: (seriesId: number) =>
    api.get<SeriesProgress>(`/api/collection/series/${seriesId}`),
  add: (volumeId: number) => api.post<unknown>(`/api/collection/volumes/${volumeId}`),
  remove: (volumeId: number) => api.delete<void>(`/api/collection/volumes/${volumeId}`),
  addRange: (seriesId: number, from: number, to: number) =>
    api.post<{ added: number }>(`/api/collection/series/${seriesId}/range?from=${from}&to=${to}`),
}
