import { Navigate, Outlet, useLocation } from 'react-router'
import { t } from '../../i18n/t'
import NotFoundPage from '../../components/NotFoundPage'
import { useMe } from './useMe'

export default function RequireAuth({ admin = false }: { admin?: boolean }) {
  const { data: me, isPending, isError, refetch } = useMe()
  const location = useLocation()

  if (isPending) return <p className="muted">{t('common.loading')}</p>
  if (isError)
    return (
      <div className="app-state">
        <p role="alert">{t('error.generic')}</p>
        <button onClick={() => refetch()}>{t('common.retry')}</button>
      </div>
    )
  if (!me)
    return (
      <Navigate
        to="/login"
        state={{ from: location.pathname + location.search }}
        replace
      />
    )
  if (me.mustChangePassword) return <Navigate to="/change-password" replace />
  if (admin && me.role !== 'ADMIN') return <NotFoundPage />
  return <Outlet />
}
