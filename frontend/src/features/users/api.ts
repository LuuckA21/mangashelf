import {
  useMutation,
  useQuery,
  useQueryClient,
  keepPreviousData,
} from '@tanstack/react-query'
import { api, json } from '../../api/client'
import type { Page, Role, UserResponse } from '../../api/types'

/** `page` is 1-based. */
export function useUsers(page: number) {
  return useQuery({
    queryKey: ['users', page],
    placeholderData: keepPreviousData,
    queryFn: () => api<Page<UserResponse>>(`/users?page=${page - 1}&size=20`),
  })
}

export function useUserMutations() {
  const client = useQueryClient()
  const refresh = () => client.invalidateQueries({ queryKey: ['users'] })
  return {
    create: useMutation({
      mutationFn: (v: {
        username: string
        temporaryPassword: string
        role: Role
      }) => api('/users', json('POST', v)),
      onSuccess: refresh,
    }),
    reset: useMutation({
      mutationFn: (v: { id: string; temporaryPassword: string }) =>
        api(
          `/users/${v.id}/reset-password`,
          json('POST', { temporaryPassword: v.temporaryPassword }),
        ),
    }),
    changeRole: useMutation({
      mutationFn: (v: { id: string; role: Role }) =>
        api(`/users/${v.id}`, json('PATCH', { role: v.role })),
      onSuccess: refresh,
    }),
    remove: useMutation({
      mutationFn: (id: string) => api(`/users/${id}`, { method: 'DELETE' }),
      onSuccess: refresh,
    }),
  }
}
