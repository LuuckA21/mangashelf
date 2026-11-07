import { useEffect, useRef } from 'react'
import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query'
import { ApiError, api } from '../../api/client'
import type {
  EditionResponse,
  Page,
  SeriesDetail,
  SeriesSummary,
} from '../../api/types'
import { t } from '../../i18n/t'
import type { EditionFormValues } from './EditionForm'

/** `page` is 1-based, as shown in the URL. */
export function useSeriesList(q: string, page: number) {
  return useQuery({
    queryKey: ['series', q, page],
    queryFn: () => {
      const p = new URLSearchParams({ page: String(page - 1), size: '20' })
      if (q) p.set('q', q)
      return api<Page<SeriesSummary>>(`/series?${p}`)
    },
  })
}

export function useSeriesDetail(id: string) {
  return useQuery({
    queryKey: ['series-detail', id],
    queryFn: () => api<SeriesDetail>(`/series/${id}`),
  })
}

export const errorText = (e: unknown) =>
  e instanceof ApiError ? e.detail : t('error.generic')

const json = (method: string, body: unknown): RequestInit => ({
  method,
  body: JSON.stringify(body),
})

export function useCreateSeries() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (title: string) =>
      api<SeriesSummary>('/series', json('POST', { title })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['series'] }),
  })
}

export function useRenameSeries(id: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (title: string) =>
      api<SeriesSummary>(`/series/${id}`, json('PUT', { title })),
    onSuccess: () =>
      Promise.all([
        client.invalidateQueries({ queryKey: ['series'] }),
        client.invalidateQueries({ queryKey: ['series-detail', id] }),
        client.invalidateQueries({ queryKey: ['collection'] }),
      ]),
  })
}

/**
 * Drops a deleted record's cache entry once its page has unmounted. Removing it earlier
 * would make the still-mounted page refetch it and flash a 404.
 */
function useDropOnUnmount() {
  const client = useQueryClient()
  const keys = useRef<unknown[][]>([])
  useEffect(
    () => () =>
      keys.current.forEach((queryKey) => client.removeQueries({ queryKey })),
    [client],
  )
  return (key: unknown[]) => keys.current.push(key)
}

export function useDeleteSeries(id: string) {
  const client = useQueryClient()
  const drop = useDropOnUnmount()
  return useMutation({
    mutationFn: () => api<void>(`/series/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      drop(['series-detail', id])
      return client.invalidateQueries({ queryKey: ['series'] })
    },
  })
}

function uploadCover(id: string, file: File) {
  const body = new FormData()
  body.append('file', file)
  return api<void>(`/editions/${id}/cover`, { method: 'PUT', body })
}

/** The cover is a second request: a failed upload must not undo the new edition. */
export function useCreateEdition(seriesId: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: async (v: {
      values: EditionFormValues
      cover: File | null
    }) => {
      const created = await api<EditionResponse>(
        `/series/${seriesId}/editions`,
        json('POST', v.values),
      )
      let coverError: unknown = null
      if (v.cover)
        await uploadCover(created.id, v.cover).catch((e) => (coverError = e))
      return { edition: created, coverError }
    },
    onSuccess: () => refreshEdition(client, seriesId),
  })
}

function refreshEdition(client: QueryClient, seriesId: string, id?: string) {
  return Promise.all([
    client.invalidateQueries({ queryKey: ['series-detail', seriesId] }),
    client.invalidateQueries({ queryKey: ['series'] }),
    client.invalidateQueries({ queryKey: ['collection'] }),
    ...(id ? [client.invalidateQueries({ queryKey: ['edition', id] })] : []),
  ])
}

export function useUpdateEdition(edition: EditionResponse) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (values: EditionFormValues) =>
      api<EditionResponse>(
        `/editions/${edition.id}`,
        json('PUT', { ...values, version: edition.version }),
      ),
    // Also after a 409: the refetch brings the fresh version and values.
    onSettled: () => refreshEdition(client, edition.seriesId, edition.id),
  })
}

export function useDeleteEdition(edition: EditionResponse) {
  const client = useQueryClient()
  const drop = useDropOnUnmount()
  return useMutation({
    mutationFn: () =>
      api<void>(`/editions/${edition.id}`, { method: 'DELETE' }),
    onSuccess: () => {
      drop(['edition', edition.id])
      return refreshEdition(client, edition.seriesId)
    },
  })
}

export function useSetCover(edition: EditionResponse) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (file: File | null) =>
      file
        ? uploadCover(edition.id, file)
        : api<void>(`/editions/${edition.id}/cover`, { method: 'DELETE' }),
    onSettled: () => refreshEdition(client, edition.seriesId, edition.id),
  })
}
