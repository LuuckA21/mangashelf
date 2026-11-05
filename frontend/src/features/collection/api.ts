import { useRef, useState } from 'react'
import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query'
import { api } from '../../api/client'
import type {
  CollectionEntry,
  CollectionStats,
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

/** Under the 'collection' key, so every collection invalidation refreshes it too. */
export function useCollectionStats() {
  return useQuery({
    queryKey: ['collection', 'stats'],
    queryFn: () => api<CollectionStats>('/collection/stats'),
  })
}

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

/**
 * Optimistic: the cache shows the new set at once. Saves for one edition run one at a
 * time (so they cannot arrive out of order); a failed save puts the last set the server
 * confirmed back, and `failed` stays true until a later burst of saves ends without error.
 */
export function useSetOwned(editionId: string) {
  const client = useQueryClient()
  const entryKey = ['collection-entry', editionId]
  const confirmed = useRef<number[]>([])
  const inflight = useRef(0)
  const burstFailed = useRef(false)
  const [failed, setFailed] = useState(false)

  function show(owned: number[]) {
    client.setQueriesData<Page<CollectionEntry>>(
      { queryKey: ['collection'] },
      (page) =>
        page && {
          ...page,
          content: page.content.map((e) =>
            e.editionId === editionId ? { ...e, owned } : e,
          ),
        },
    )
    client.setQueryData<Owned>(entryKey, (old) => ({ ...old, owned }))
  }

  const mutation = useMutation({
    scope: { id: `owned-${editionId}` },
    mutationFn: (owned: number[]) =>
      api<CollectionEntry>(`/collection/editions/${editionId}/volumes`, {
        method: 'PUT',
        body: JSON.stringify({ owned }),
      }),
    onMutate: async (owned) => {
      await client.cancelQueries({ queryKey: ['collection'] })
      await client.cancelQueries({ queryKey: entryKey })
      if (inflight.current++ === 0) {
        confirmed.current = client.getQueryData<Owned>(entryKey)?.owned ?? []
        burstFailed.current = false
      }
      show(owned)
    },
    onSuccess: (saved) => {
      confirmed.current = saved.owned
      if (inflight.current === 1) show(saved.owned)
    },
    onError: () => {
      burstFailed.current = true
      setFailed(true)
      show(confirmed.current)
    },
    // Refetching while saves are queued would overwrite the newer optimistic set.
    onSettled: () => {
      if (--inflight.current > 0) return
      // An alert stays up until a later burst of saves ends cleanly.
      setFailed(burstFailed.current)
      return refresh(client, editionId)
    },
  })
  return { ...mutation, failed }
}

export function useAddToCollection(editionId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () =>
      api<CollectionEntry>(`/collection/editions/${editionId}`, {
        method: 'PUT',
      }),
    onSettled: () =>
      Promise.all([
        refresh(client, editionId),
        client.invalidateQueries({ queryKey: ['series-detail'] }),
      ]),
  })
}

export function useRemoveFromCollection(editionId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () =>
      api<void>(`/collection/editions/${editionId}`, { method: 'DELETE' }),
    onSuccess: () => {
      // The entry is gone server side: refetching it would only answer 404.
      client.setQueryData<EditionResponse>(
        ['edition', editionId],
        (e) => e && { ...e, inMyCollection: false },
      )
      client.removeQueries({ queryKey: ['collection-entry', editionId] })
    },
    onSettled: () =>
      Promise.all([
        client.invalidateQueries({ queryKey: ['collection'] }),
        client.invalidateQueries({ queryKey: ['edition', editionId] }),
      ]),
  })
}
