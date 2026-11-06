import { useNavigate } from 'react-router'
import { ApiError } from '../../api/client'
import { t } from '../../i18n/t'
import ChangePasswordForm from '../auth/ChangePasswordForm'
import { useExportData } from './api'
import PurchaseSettingsForm from './PurchaseSettingsForm'
import DeleteAccount from './DeleteAccount'
import { useIsPhone } from '../../components/useIsPhone'
import { useLogout } from '../auth/useLogout'

export default function AccountPage() {
  const navigate = useNavigate()
  const exportData = useExportData()
  const exportError = exportData.error
  // On a phone the header has no room for Esci: it lives here.
  const phone = useIsPhone()
  const { logout, failed: logoutFailed } = useLogout()
  return (
    <>
      <div className="page-head">
        <h1>{t('account.title')}</h1>
        {phone && (
          <button className="quiet" onClick={logout}>
            {t('nav.logout')}
          </button>
        )}
      </div>
      {phone && logoutFailed && (
        <p className="error" role="alert">
          {t('error.generic')}
        </p>
      )}

      <section className="panel settings-panel settings-section">
        <h2>{t('account.passwordTitle')}</h2>
        <p className="muted">{t('account.passwordHelp')}</p>
        <ChangePasswordForm
          onSuccess={() =>
            navigate('/login', {
              replace: true,
              state: { passwordChanged: true },
            })
          }
        />
      </section>

      <PurchaseSettingsForm />

      <section className="panel settings-panel settings-section">
        <h2>{t('account.exportTitle')}</h2>
        <p className="muted">{t('account.exportHelp')}</p>
        {exportError && (
          <p className="error" role="alert">
            {exportError instanceof ApiError
              ? exportError.detail
              : t('error.generic')}
          </p>
        )}
        <button
          className="quiet"
          disabled={exportData.isPending}
          onClick={() => exportData.mutate()}
        >
          {t('account.export')}
        </button>
      </section>

      <DeleteAccount />
    </>
  )
}
