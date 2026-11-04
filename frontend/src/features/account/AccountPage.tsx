import { useNavigate } from 'react-router'
import { ApiError } from '../../api/client'
import { t } from '../../i18n/t'
import ChangePasswordForm from '../auth/ChangePasswordForm'
import { useExportData } from './api'
import DeleteAccount from './DeleteAccount'

export default function AccountPage() {
  const navigate = useNavigate()
  const exportData = useExportData()
  const exportError = exportData.error
  return (
    <>
      <div className="page-head">
        <h1>{t('account.title')}</h1>
      </div>

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
