import { useMutation } from '@tanstack/react-query'
import { api } from '../../api/client'

export function useExportData() {
  return useMutation({
    mutationFn: async () => {
      const data = await api<unknown>('/account/export')
      const url = URL.createObjectURL(
        new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }),
      )
      const a = document.createElement('a')
      a.href = url
      a.download = 'mangashelf-export.json'
      document.body.append(a)
      a.click()
      a.remove()
      setTimeout(() => URL.revokeObjectURL(url), 0)
    },
  })
}

export function useDeleteAccount() {
  return useMutation({
    mutationFn: (password: string) =>
      api('/account', { method: 'DELETE', body: JSON.stringify({ password }) }),
  })
}
