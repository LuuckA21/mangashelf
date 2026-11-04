import {
  useMutation,
  useQuery,
  useQueryClient,
  keepPreviousData,
} from '@tanstack/react-query'
import { api } from '../../api/client'
import type { Page, Role, UserResponse } from '../../api/types'

const send = (method: string, body: unknown): RequestInit => ({
  method,
  body: JSON.stringify(body),
})

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
      }) => api('/users', send('POST', v)),
      onSuccess: refresh,
    }),
    reset: useMutation({
      mutationFn: (v: { id: string; temporaryPassword: string }) =>
        api(
          `/users/${v.id}/reset-password`,
          send('POST', { temporaryPassword: v.temporaryPassword }),
        ),
    }),
    changeRole: useMutation({
      mutationFn: (v: { id: string; role: Role }) =>
        api(`/users/${v.id}`, send('PATCH', { role: v.role })),
      onSuccess: refresh,
    }),
    remove: useMutation({
      mutationFn: (id: string) => api(`/users/${id}`, { method: 'DELETE' }),
      onSuccess: refresh,
    }),
  }
}
