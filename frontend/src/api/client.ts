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

export interface EditionSummary {
  seriesId: number
  seriesName: string
  publisher: string
  mangaId: number
  mangaTitle: string
  coverUrl: string | null
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

/**
 * Spring Data's paged wrapper.
 *
 * With pageSerializationMode VIA_DTO the metadata sits under "page", while
 * the list stays in "content" in both shapes — and "content" is the only
 * field this client actually needs.
 */
interface Page<T> {
  content: T[]
  page?: { size: number; number: number; totalElements: number; totalPages: number }
}

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

  /**
   * Uploads a cover image. Goes through fetch directly because the body is
   * multipart: setting Content-Type by hand would omit the boundary the
   * browser generates, and the server would not be able to split the parts.
   */
  uploadCover: async (id: number, file: File): Promise<Manga> => {
    const body = new FormData()
    body.append('file', file)

    const token = document.cookie.match(/XSRF-TOKEN=([^;]+)/)?.[1]
    const response = await fetch(`/api/manga/${id}/cover`, {
      method: 'POST',
      headers: token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {},
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

export interface PurchaseItem {
  id: number
  seriesId: number
  seriesName: string
  publisher: string
  mangaTitle: string
  volumeNumber: number
  releaseDate: string | null
  priceEurCents: number | null
  priceChfCents: number | null
  reserved: boolean
}

export interface PurchaseList {
  id: number
  name: string
  periodYear: number | null
  periodMonth: number | null
  paidAt: string | null
  discountPercent: string | null
  discountCents: number | null
  items: PurchaseItem[]
  reservedCount: number
  totalEurCents: number
  subtotalChfCents: number
  discountAppliedCents: number
  totalChfCents: number
}

export interface PurchaseListSummary {
  id: number
  name: string
  periodYear: number | null
  periodMonth: number | null
  paidAt: string | null
  itemCount: number
  reservedCount: number
  totalChfCents: number
}

interface ListBody {
  name: string
  periodYear?: number | null
  periodMonth?: number | null
  discountPercent?: string | null
  discountCents?: number | null
}

export const purchases = {
  listAll: () => api.get<PurchaseListSummary[]>('/api/purchases'),
  get: (id: number) => api.get<PurchaseList>(`/api/purchases/${id}`),
  create: (body: ListBody) => api.post<PurchaseList>('/api/purchases', body),
  update: (id: number, body: ListBody) =>
    api.put<PurchaseList>(`/api/purchases/${id}`, body),
  setPaid: (id: number, paid: boolean) =>
    api.put<PurchaseList>(`/api/purchases/${id}/paid`, { paid }),
  setReserved: (id: number, itemId: number, reserved: boolean) =>
    api.put<PurchaseList>(`/api/purchases/${id}/items/${itemId}/reserved`, { reserved }),
  remove: (id: number) => api.delete<void>(`/api/purchases/${id}`),
  addItem: (id: number, body: {
    seriesId: number
    volumeNumber: number
    releaseDate?: string | null
    priceEurCents?: number | null
    priceChfCents?: number | null
  }) => api.post<PurchaseList>(`/api/purchases/${id}/items`, body),
  removeItem: (id: number, itemId: number) =>
    api.delete<PurchaseList>(`/api/purchases/${id}/items/${itemId}`),
}

export const collection = {
  listOwned: () => api.get<OwnedVolume[]>('/api/collection/volumes'),
  summary: () => api.get<EditionSummary[]>('/api/collection/summary'),
  progress: (seriesId: number) =>
    api.get<SeriesProgress>(`/api/collection/series/${seriesId}`),
  add: (volumeId: number) => api.post<unknown>(`/api/collection/volumes/${volumeId}`),
  remove: (volumeId: number) => api.delete<void>(`/api/collection/volumes/${volumeId}`),
  addRange: (seriesId: number, from: number, to: number) =>
    api.post<{ added: number }>(`/api/collection/series/${seriesId}/range?from=${from}&to=${to}`),
}
