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
}

export interface SeriesProgress {
  seriesId: number
  seriesName: string
  mangaTitle: string
  /** Announced volume count, when the edition declares one. */
  declaredTotal: number | null
  /** Where the shelf stops: the declared total, or the highest volume owned. */
  upTo: number
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
  declaredTotal: number | null
  completed: boolean
  upTo: number
  ownedCount: number
  ownedNumbers: number[]
  missingNumbers: number[]
}

export interface OwnedVolume {
  seriesId: number
  number: number
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
 * the list stays in "content". Keep Spring's wire shape private and expose a
 * flat result to pages and pickers, so callers cannot accidentally discard
 * the information needed to request the next page.
 */
interface SpringPage<T> {
  content: T[]
  page?: { size: number; number: number; totalElements: number; totalPages: number }
}

export interface PageResult<T> {
  content: T[]
  size: number
  number: number
  totalElements: number
  totalPages: number
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
  listManga: (q = '', page = 0, size = 24) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    const query = q.trim()
    if (query) params.set('q', query)
    return api.get<SpringPage<Manga>>(`/api/manga?${params}`)
      .then(pageResult)
  },
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
  purchasedAt: string | null
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
  purchasedCount: number
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
  purchasedCount: number
  totalChfCents: number
}

interface ListBody {
  name: string
  periodYear?: number | null
  periodMonth?: number | null
  discountPercent?: string | null
  discountCents?: number | null
}

export interface PurchaseSuggestion {
  seriesId: number
  seriesName: string
  publisher: string
  mangaTitle: string
  volumeNumber: number
  priceEurCents: number | null
  priceChfCents: number | null
  lastBoughtIn: string
}

export interface YearStats {
  year: number
  listCount: number
  volumeCount: number
  fullChfCents: number
  discountChfCents: number
  netChfCents: number
  averageFullChfCents: number
  averageNetChfCents: number
}

export interface PurchaseStats {
  years: YearStats[]
  listCount: number
  volumeCount: number
  fullChfCents: number
  discountChfCents: number
  netChfCents: number
  averageFullChfCents: number
  averageNetChfCents: number
}

export interface TransferResult {
  added: number
  alreadyOwned: number
}

export const purchases = {
  toCollection: (id: number) =>
    api.post<TransferResult>(`/api/purchases/${id}/to-collection`),
  stats: () => api.get<PurchaseStats>('/api/purchases/stats'),
  suggestions: (id: number) =>
    api.get<PurchaseSuggestion[]>(`/api/purchases/${id}/suggestions`),
  listAll: () => api.get<PurchaseListSummary[]>('/api/purchases'),
  get: (id: number) => api.get<PurchaseList>(`/api/purchases/${id}`),
  create: (body: ListBody) => api.post<PurchaseList>('/api/purchases', body),
  update: (id: number, body: ListBody) =>
    api.put<PurchaseList>(`/api/purchases/${id}`, body),
  setPaid: (id: number, paid: boolean) =>
    api.put<PurchaseList>(`/api/purchases/${id}/paid`, { paid }),
  setReserved: (id: number, itemId: number, reserved: boolean) =>
    api.put<PurchaseList>(`/api/purchases/${id}/items/${itemId}/reserved`, { reserved }),
  setPurchased: (id: number, itemId: number, purchased: boolean) =>
    api.put<PurchaseList>(`/api/purchases/${id}/items/${itemId}/purchased`, { purchased }),
  carryOver: (targetId: number, sourceId: number) =>
    api.post<{ moved: number }>(`/api/purchases/${targetId}/carry-over/${sourceId}`),
  remove: (id: number) => api.delete<void>(`/api/purchases/${id}`),
  addItem: (id: number, body: {
    seriesId: number
    volumeNumber: number
    releaseDate?: string | null
    priceEurCents?: number | null
    priceChfCents?: number | null
  }) => api.post<PurchaseList>(`/api/purchases/${id}/items`, body),
  updateItem: (id: number, itemId: number, body: {
    seriesId: number
    volumeNumber: number
    releaseDate?: string | null
    priceEurCents?: number | null
    priceChfCents?: number | null
  }) => api.put<PurchaseList>(`/api/purchases/${id}/items/${itemId}`, body),
  removeItem: (id: number, itemId: number) =>
    api.delete<PurchaseList>(`/api/purchases/${id}/items/${itemId}`),
}

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
