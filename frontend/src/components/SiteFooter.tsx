import { Link } from 'react-router'
import { t } from '../i18n/t'

const FIRST_YEAR = 2026

/** Shown on every page. Version from backend/pom.xml; commit only on staging builds. */
export default function SiteFooter() {
  const year = new Date().getFullYear()
  const years = year > FIRST_YEAR ? `${FIRST_YEAR}–${year}` : `${FIRST_YEAR}`
  const commit = String(import.meta.env.VITE_APP_COMMIT ?? '').slice(0, 7)
  const version = `v${import.meta.env.VITE_APP_VERSION}${commit ? ` (${commit})` : ''}`

  return (
    <footer className="site-footer">
      <span>
        © {years} {t('app.name')}
      </span>
      <span>{version}</span>
      <Link to="/privacy">{t('footer.privacy')}</Link>
    </footer>
  )
}
