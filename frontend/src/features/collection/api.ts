import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query'
import { api } from '../../api/client'
import type {
  CollectionEntry,
  EditionResponse,
  EditionStatus,
  EditionType,
  Page,
} from '../../api/types'

export type CollectionFilters = {
  q?: string
  type?: EditionType
  status?: EditionStatus
  incomplete?: boolean
  /** 1-based, as shown in the URL. */
  page: number
}

type Owned = { owned: number[] }

export function useCollection(filters: CollectionFilters) {
  return useQuery({
    queryKey: ['collection', filters],
    queryFn: () => {
      const p = new URLSearchParams({
        page: String(filters.page - 1),
        size: '20',
      })
      if (filters.q) p.set('q', filters.q)
      if (filters.type) p.set('type', filters.type)
      if (filters.status) p.set('status', filters.status)
      if (filters.incomplete) p.set('incomplete', 'true')
      return api<Page<CollectionEntry>>(`/collection?${p}`)
    },
  })
}

export function useEdition(id: string) {
  return useQuery({
    queryKey: ['edition', id],
    queryFn: () => api<EditionResponse>(`/editions/${id}`),
  })
}

/** The owned volumes; only fetched for an edition already in the collection. */
export function useOwned(id: string, inCollection: boolean) {
  return useQuery({
    queryKey: ['collection-entry', id],
    enabled: inCollection,
    queryFn: () => api<CollectionEntry>(`/collection/editions/${id}`),
  })
}

function refresh(client: QueryClient, id: string) {
  return Promise.all([
    client.invalidateQueries({ queryKey: ['collection'] }),
    client.invalidateQueries({ queryKey: ['edition', id] }),
    client.invalidateQueries({ queryKey: ['collection-entry', id] }),
  ])
}

/** Optimistic: the cache shows the new set at once and is restored if the save fails. */
export function useSetOwned(editionId: string) {
  const client = useQueryClient()
  const mutationKey = ['set-owned', editionId]
  const entryKey = ['collection-entry', editionId]
  return useMutation({
    mutationKey,
    mutationFn: (owned: number[]) =>
      api<CollectionEntry>(`/collection/editions/${editionId}/volumes`, {
        method: 'PUT',
        body: JSON.stringify({ owned }),
      }),
    onMutate: async (owned) => {
      await client.cancelQueries({ queryKey: ['collection'] })
      await client.cancelQueries({ queryKey: entryKey })
      const lists = client.getQueriesData<Page<CollectionEntry>>({
        queryKey: ['collection'],
      })
      const entry = client.getQueryData<Owned>(entryKey)
      for (const [key] of lists)
        client.setQueryData<Page<CollectionEntry>>(
          key,
          (page) =>
            page && {
              ...page,
              content: page.content.map((e) =>
                e.editionId === editionId ? { ...e, owned } : e,
              ),
            },
        )
      client.setQueryData<Owned>(entryKey, (old) => ({ ...old, owned }))
      return { lists, entry }
    },
    onError: (_e, _owned, ctx) => {
      if (!ctx) return
      for (const [key, data] of ctx.lists) client.setQueryData(key, data)
      client.setQueryData<Owned>(entryKey, ctx.entry ?? { owned: [] })
    },
    // With several saves in flight, refetching early would overwrite the newer optimistic set.
    onSettled: () =>
      client.isMutating({ mutationKey }) === 1
        ? refresh(client, editionId)
        : undefined,
  })
}

export function useAddToCollection(editionId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () =>
      api<CollectionEntry>(`/collection/editions/${editionId}`, {
        method: 'PUT',
      }),
    onSettled: () => refresh(client, editionId),
  })
}

export function useRemoveFromCollection(editionId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () =>
      api<void>(`/collection/editions/${editionId}`, { method: 'DELETE' }),
    onSuccess: () =>
      client.setQueryData<Owned>(['collection-entry', editionId], {
        owned: [],
      }),
    onSettled: () => refresh(client, editionId),
  })
}
