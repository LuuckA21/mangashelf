import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { api } from '../../api/client'
import type {
  ListDetail,
  ListSummary,
  Proposal,
  PurchaseSettings,
  SpendingSummary,
} from '../../api/types'

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

export function usePurchaseSummary(year: number) {
  return useQuery({
    queryKey: ['purchase-summary', year],
    queryFn: () => api<SpendingSummary>(`/purchase-summary?year=${year}`),
    placeholderData: keepPreviousData,
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
    onSuccess: () =>
      Promise.all(
        ['purchase-lists', 'purchase-summary'].map((k) =>
          client.invalidateQueries({ queryKey: [k] }),
        ),
      ),
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
    onSuccess: (saved) => {
      client.setQueryData(['purchase-settings'], saved)
      return client.invalidateQueries({ queryKey: ['purchase-summary'] })
    },
  })
}

/** Any change to a list: the answer is the new detail. A row in a closed list changes owned volumes. */
export function useListChange(id: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (c: { method: string; path?: string; body?: unknown }) =>
      api<ListDetail>(
        `/purchase-lists/${id}${c.path ?? ''}`,
        c.body === undefined ? { method: c.method } : json(c.method, c.body),
      ),
    onSuccess: (detail) => {
      client.setQueryData(['purchase-list', id], detail)
      return Promise.all(
        [
          'purchase-lists',
          'purchase-summary',
          'collection',
          'edition',
          'collection-entry',
          'series-detail',
        ].map((k) => client.invalidateQueries({ queryKey: [k] })),
      )
    },
  })
}

export const fetchProposal = (id: string, editionId: string) =>
  api<Proposal>(`/purchase-lists/${id}/proposal?editionId=${editionId}`)
