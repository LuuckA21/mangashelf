import { NavLink, Link } from 'react-router-dom'
import { auth } from '../api/client'
import { useSession } from '../api/session'
import { useState, type ReactNode } from 'react'
import { useI18n } from '../i18n'

export default function Layout({ children }: { children: ReactNode }) {
  const { user, setUser } = useSession()
  const { t } = useI18n()
  const [error, setError] = useState<string | null>(null)

  async function handleLogout() {
    setError(null)
    try {
      await auth.logout()
      setUser(null)
    } catch {
      setError(t('nav.logoutFailed'))
    }
  }

  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand">
            MangaShelf
          </Link>
          <nav aria-label={t('nav.main')}>
            <NavLink to="/" end>
              {t('nav.catalog')}
            </NavLink>
            <NavLink to="/collection">{t('nav.collection')}</NavLink>
            <NavLink to="/purchases">{t('nav.purchases')}</NavLink>
            {user?.role === 'ADMIN' && (
              <NavLink to="/admin/users">{t('nav.users')}</NavLink>
            )}
          </nav>
          <span className="spacer" />
          <Link
            to="/settings"
            className="account-link"
            aria-label={`${t('nav.settings')}: ${user?.username ?? ''}`}
            title={user?.username}
          >
            {user?.username}
          </Link>
          <button className="quiet" onClick={handleLogout}>
            {t('nav.logout')}
          </button>
        </div>
      </header>
      <main className="page">
        {error && (
          <div className="error" role="alert">
            {error}
          </div>
        )}
        {children}
      </main>
    </>
  )
}
