import { Suspense } from 'react'
import { NavLink, Link, Outlet } from 'react-router'
import { useMe } from '../features/auth/useMe'
import { t } from '../i18n/t'
import AdminMenu from './AdminMenu'
import NavIcon from './NavIcon'

export default function Layout() {
  const { data: me } = useMe()
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
              {/* Top bar on a computer, bottom tab bar on a phone (styles.css). */}
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
                <NavLink to="/account">
                  <NavIcon name="account" />
                  {t('nav.account')}
                </NavLink>
              </nav>
              <span className="spacer" />
              {admin && <AdminMenu />}
            </>
          )}
        </div>
      </header>
      <main className="page">
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
