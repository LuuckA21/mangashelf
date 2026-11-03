import { useQuery } from '@tanstack/react-query'
import { ApiError, api } from '../../api/client'
import type { MeResponse } from '../../api/types'

// A 401 means "nobody logged in", which is an answer, not an error.
export function useMe() {
  return useQuery({
    queryKey: ['me'],
    staleTime: 60_000,
    queryFn: () =>
      api<MeResponse>('/auth/me').catch((e) => {
        if (e instanceof ApiError && e.status === 401) return null
        throw e
      }),
  })
}
