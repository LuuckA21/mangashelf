import { Navigate, useNavigate } from 'react-router'
import { t } from '../../i18n/t'
import ChangePasswordForm from './ChangePasswordForm'
import { useLogout } from './useLogout'
import { useMe } from './useMe'

export default function ChangePasswordPage() {
  const { data: me, isPending } = useMe()
  const navigate = useNavigate()
  const { logout, failed } = useLogout()

  if (isPending) return <p className="app-state">{t('common.loading')}</p>
  if (!me) return <Navigate to="/login" replace />

  return (
    <div className="auth-shell">
      <main className="auth-card">
        <h1>{t('auth.changePassword.title')}</h1>
        <p className="subtitle">{t('auth.changePassword.intro')}</p>
        <ChangePasswordForm
          onSuccess={() =>
            navigate('/login', {
              replace: true,
              state: { passwordChanged: true },
            })
          }
        />
        {failed && (
          <p className="error" role="alert">
            {t('error.generic')}
          </p>
        )}
        <button type="button" className="quiet" onClick={logout}>
          {t('nav.logout')}
        </button>
      </main>
    </div>
  )
}
