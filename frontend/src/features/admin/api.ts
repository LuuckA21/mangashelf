import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { Publisher } from '../../api/types'

const send = (method: string, body: unknown): RequestInit => ({
  method,
  body: JSON.stringify(body),
})

/** Sorted by name (case-insensitive) by the server. */
export function usePublishers() {
  return useQuery({
    queryKey: ['publishers'],
    queryFn: () => api<Publisher[]>('/publishers'),
  })
}

export function usePublisherMutations() {
  const client = useQueryClient()
  const refresh = () => client.invalidateQueries({ queryKey: ['publishers'] })
  // Edition views show the publisher name, so a rename or delete reaches them.
  const refreshAll = () =>
    Promise.all(
      ['publishers', 'series-detail', 'edition', 'collection'].map((k) =>
        client.invalidateQueries({ queryKey: [k] }),
      ),
    )
  return {
    create: useMutation({
      mutationFn: (name: string) =>
        api<Publisher>('/publishers', send('POST', { name })),
      onSuccess: refresh,
    }),
    rename: useMutation({
      mutationFn: (v: { id: string; name: string }) =>
        api<Publisher>(`/publishers/${v.id}`, send('PATCH', { name: v.name })),
      onSuccess: refreshAll,
    }),
    remove: useMutation({
      mutationFn: (id: string) =>
        api(`/publishers/${id}`, { method: 'DELETE' }),
      onSuccess: refreshAll,
    }),
  }
}
