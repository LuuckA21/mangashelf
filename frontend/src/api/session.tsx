import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { auth, type User } from './client'

interface Session {
  user: User | null
  loading: boolean
  setUser: (user: User | null) => void
}

const SessionContext = createContext<Session | null>(null)

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  // On first paint we do not know whether a session cookie is still valid,
  // so we ask the server rather than trusting anything stored client-side.
  useEffect(() => {
    auth
      .me()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setLoading(false))
  }, [])

  return (
    <SessionContext.Provider value={{ user, loading, setUser }}>
      {children}
    </SessionContext.Provider>
  )
}

export function useSession(): Session {
  const context = useContext(SessionContext)
  if (!context) throw new Error('useSession must be used inside SessionProvider')
  return context
}
