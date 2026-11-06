import { Suspense } from 'react'
import { NavLink, Link, Outlet } from 'react-router'
import { useLogout } from '../features/auth/useLogout'
import { useMe } from '../features/auth/useMe'
import { t } from '../i18n/t'
import AdminMenu from './AdminMenu'
import NavIcon from './NavIcon'
import { useIsPhone } from './useIsPhone'

export default function Layout() {
  const { data: me } = useMe()
  const { logout, failed: logoutFailed } = useLogout()
  const phone = useIsPhone()
  const admin = me?.role === 'ADMIN'

  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <Link className="brand" to="/">
            {t('app.name')}
          </Link>
          {me && !me.mustChangePassword && (
            <>
              {/* On a phone this nav becomes the bottom tab bar (styles.css). */}
              <nav aria-label={t('nav.main')}>
                <NavLink to="/" end>
                  <NavIcon name="collection" />
                  {t('nav.collection')}
                </NavLink>
                <NavLink to="/catalog">
                  <NavIcon name="catalog" />
                  {t('nav.catalog')}
                </NavLink>
                <NavLink to="/purchases">
                  <NavIcon name="purchases" />
                  {t('nav.purchases')}
                </NavLink>
                {admin && !phone && (
                  <>
                    <NavLink to="/admin/users">{t('nav.users')}</NavLink>
                    <NavLink to="/admin" end>
                      {t('nav.admin')}
                    </NavLink>
                  </>
                )}
                <NavLink to="/account">
                  <NavIcon name="account" />
                  {t('nav.account')}
                </NavLink>
              </nav>
              <span className="spacer" />
              {phone ? (
                admin && <AdminMenu />
              ) : (
                <button className="quiet" onClick={logout}>
                  {t('nav.logout')}
                </button>
              )}
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
