import { Link } from 'react-router'
import { t } from '../i18n/t'

export default function NotFoundPage() {
  return (
    <>
      <div className="page-head">
        <h1>{t('notFound.title')}</h1>
      </div>
      <Link to="/">{t('notFound.back')}</Link>
    </>
  )
}
