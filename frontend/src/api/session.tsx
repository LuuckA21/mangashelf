import {
  createContext, useCallback, useContext, useEffect, useState, type ReactNode,
} from 'react'
import { ApiError, auth, type User } from './client'
import { useI18n, type Language } from '../i18n'

interface Session {
  user: User | null
  loading: boolean
  unavailable: boolean
  setUser: (user: User | null) => void
  retry: () => void
  updateLanguage: (language: Language) => Promise<void>
  updateProfile: (username: string, email: string, currentPassword: string) => Promise<void>
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>
  deleteAccount: (currentPassword: string) => Promise<void>
}

const SessionContext = createContext<Session | null>(null)

export function isExpiredSession(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const { setLanguage } = useI18n()
  const [user, setUserState] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [unavailable, setUnavailable] = useState(false)

  const setUser = useCallback((next: User | null) => {
    setUserState(next)
    if (next) setLanguage(next.language)
  }, [setLanguage])

  const restore = useCallback(() => {
    setLoading(true)
    setUnavailable(false)
    auth.me()
      .then(setUser)
      .catch((error) => {
        if (isExpiredSession(error)) {
          setUser(null)
        } else {
          setUnavailable(true)
        }
      })
      .finally(() => setLoading(false))
  }, [setUser])

  // On first paint we do not know whether a session cookie is still valid,
  // so we ask the server rather than trusting anything stored client-side.
  useEffect(() => {
    restore()
  }, [restore])

  async function updateLanguage(language: Language) {
    setUser(await auth.updateLanguage(language))
  }

  async function updateProfile(username: string, email: string, currentPassword: string) {
    setUser(await auth.updateProfile(username, email, currentPassword))
  }

  async function changePassword(currentPassword: string, newPassword: string) {
    await auth.changePassword(currentPassword, newPassword)
  }

  async function deleteAccount(currentPassword: string) {
    await auth.deleteAccount(currentPassword)
    setUser(null)
  }

  return (
    <SessionContext.Provider value={{
      user, loading, unavailable, setUser, retry: restore, updateLanguage,
      updateProfile, changePassword, deleteAccount,
    }}>
      {children}
    </SessionContext.Provider>
  )
}

export function useSession(): Session {
  const context = useContext(SessionContext)
  if (!context) throw new Error('useSession must be used inside SessionProvider')
  return context
}
