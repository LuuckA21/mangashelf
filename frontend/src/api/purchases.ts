import { api } from './http'
import type {
  PurchaseList,
  PurchaseListSummary,
  PurchaseStats,
  PurchaseSuggestion,
  TransferResult,
} from './types'

interface ListBody {
  name: string
  periodYear?: number | null
  periodMonth?: number | null
  discountPercent?: string | null
  discountCents?: number | null
}

interface ItemBody {
  seriesId: number
  volumeNumber: number
  releaseDate?: string | null
  priceEurCents?: number | null
  priceChfCents?: number | null
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
    api.put<PurchaseList>(`/api/purchases/${id}/items/${itemId}/reserved`, {
      reserved,
    }),
  setPurchased: (id: number, itemId: number, purchased: boolean) =>
    api.put<PurchaseList>(`/api/purchases/${id}/items/${itemId}/purchased`, {
      purchased,
    }),
  carryOver: (targetId: number, sourceId: number) =>
    api.post<{ moved: number }>(
      `/api/purchases/${targetId}/carry-over/${sourceId}`,
    ),
  remove: (id: number) => api.delete<void>(`/api/purchases/${id}`),
  addItem: (id: number, body: ItemBody) =>
    api.post<PurchaseList>(`/api/purchases/${id}/items`, body),
  updateItem: (id: number, itemId: number, body: ItemBody) =>
    api.put<PurchaseList>(`/api/purchases/${id}/items/${itemId}`, body),
  removeItem: (id: number, itemId: number) =>
    api.delete<PurchaseList>(`/api/purchases/${id}/items/${itemId}`),
}
