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
      a.click()
      URL.revokeObjectURL(url)
    },
  })
}

export function useDeleteAccount() {
  return useMutation({
    mutationFn: (password: string) =>
      api('/account', { method: 'DELETE', body: JSON.stringify({ password }) }),
  })
}
