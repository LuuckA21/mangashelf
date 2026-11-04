import { t } from '../../i18n/t'

export default function PrivacyPage() {
  return (
    <>
      <div className="page-head">
        <h1>{t('privacy.title')}</h1>
      </div>
      <p className="muted">{t('privacy.updated')}</p>

      <section className="settings-section">
        <h2>{t('privacy.controllerTitle')}</h2>
        <p>{t('privacy.controller')}</p>
      </section>

      <section className="settings-section">
        <h2>{t('privacy.dataTitle')}</h2>
        <ul>
          <li>{t('privacy.data.account')}</li>
          <li>{t('privacy.data.collection')}</li>
          <li>{t('privacy.data.session')}</li>
          <li>{t('privacy.data.logs')}</li>
          <li>{t('privacy.data.backups')}</li>
        </ul>
      </section>

      <section className="settings-section">
        <h2>{t('privacy.processorsTitle')}</h2>
        <ul>
          <li>{t('privacy.processors.hetzner')}</li>
          <li>{t('privacy.processors.infomaniak')}</li>
          <li>{t('privacy.processors.github')}</li>
        </ul>
      </section>

      <section className="settings-section">
        <h2>{t('privacy.rightsTitle')}</h2>
        <ul>
          <li>{t('privacy.rights.access')}</li>
          <li>{t('privacy.rights.erasure')}</li>
          <li>{t('privacy.rights.others')}</li>
          <li>{t('privacy.rights.complaint')}</li>
        </ul>
      </section>

      <section className="settings-section">
        <h2>{t('privacy.cookiesTitle')}</h2>
        <p>{t('privacy.cookies.body')}</p>
      </section>
    </>
  )
}
