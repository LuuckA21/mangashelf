import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { ListDetail, ListSummary, PurchaseSettings } from '../../api/types'

const json = (method: string, body: unknown): RequestInit => ({
  method,
  body: JSON.stringify(body),
})

export function usePurchaseLists() {
  return useQuery({
    queryKey: ['purchase-lists'],
    queryFn: () => api<ListSummary[]>('/purchase-lists'),
  })
}

export function usePurchaseList(id: string) {
  return useQuery({
    queryKey: ['purchase-list', id],
    queryFn: () => api<ListDetail>(`/purchase-lists/${id}`),
  })
}

export function useCreatePurchaseList() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: { description: string; month: number; year: number }) =>
      api<ListSummary>('/purchase-lists', json('POST', body)),
    onSuccess: () => client.invalidateQueries({ queryKey: ['purchase-lists'] }),
  })
}

export function usePurchaseSettings() {
  return useQuery({
    queryKey: ['purchase-settings'],
    queryFn: () => api<PurchaseSettings>('/account/purchase-settings'),
  })
}

export function useSavePurchaseSettings() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: PurchaseSettings) =>
      api<PurchaseSettings>('/account/purchase-settings', json('PUT', body)),
    onSuccess: (saved) => client.setQueryData(['purchase-settings'], saved),
  })
}
