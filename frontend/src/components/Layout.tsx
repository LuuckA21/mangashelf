import { useQueryClient } from '@tanstack/react-query'
import { Suspense, useState } from 'react'
import { NavLink, Link, Outlet, useNavigate } from 'react-router'
import { api } from '../api/client'
import { endSession } from '../features/auth/endSession'
import { useMe } from '../features/auth/useMe'
import { t } from '../i18n/t'

export default function Layout() {
  const { data: me } = useMe()
  const client = useQueryClient()
  const navigate = useNavigate()

  const [logoutFailed, setLogoutFailed] = useState(false)

  async function logout() {
    try {
      await api('/auth/logout', { method: 'POST' })
    } catch {
      setLogoutFailed(true)
      return
    }
    endSession(client)
    navigate('/login')
  }

  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <Link className="brand" to="/">
            {t('app.name')}
          </Link>
          {me && !me.mustChangePassword && (
            <>
              <nav aria-label={t('nav.main')}>
                <NavLink to="/" end>
                  {t('nav.collection')}
                </NavLink>
                <NavLink to="/catalog">{t('nav.catalog')}</NavLink>
                {me.role === 'ADMIN' && (
                  <NavLink to="/admin/users">{t('nav.users')}</NavLink>
                )}
                <NavLink to="/account">{t('nav.account')}</NavLink>
              </nav>
              <span className="spacer" />
              <button className="quiet" onClick={logout}>
                {t('nav.logout')}
              </button>
            </>
          )}
        </div>
      </header>
      <main className="page">
        {logoutFailed && (
          <p className="error" role="alert">
            {t('error.generic')}
          </p>
        )}
        <Suspense fallback={<p className="muted">{t('common.loading')}</p>}>
          <Outlet />
        </Suspense>
      </main>
      <footer className="page">
        <Link to="/privacy">{t('footer.privacy')}</Link>
      </footer>
    </>
  )
}
