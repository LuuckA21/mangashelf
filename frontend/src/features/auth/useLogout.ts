import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../../api/client'
import { endSession } from './endSession'

export function useLogout() {
  const client = useQueryClient()
  const navigate = useNavigate()
  const [failed, setFailed] = useState(false)

  async function logout() {
    setFailed(false)
    try {
      await api('/auth/logout', { method: 'POST' })
    } catch {
      setFailed(true)
      return
    }
    endSession(client)
    navigate('/login')
  }

  return { logout, failed }
}
