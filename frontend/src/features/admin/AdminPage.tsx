import { t } from '../../i18n/t'
import PublishersSection from './PublishersSection'

export default function AdminPage() {
  return (
    <>
      <div className="page-head">
        <h1>{t('admin.title')}</h1>
      </div>
      <PublishersSection />
    </>
  )
}
