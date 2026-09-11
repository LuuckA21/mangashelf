import { useEffect, useState } from 'react'
import { adminAudit, type AdminAuditEvent } from '../api/client'
import Layout from '../components/Layout'
import { useI18n } from '../i18n'

export default function AdminAudit() {
  const { locale, t } = useI18n()
  const [events, setEvents] = useState<AdminAuditEvent[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    adminAudit
      .list()
      .then(setEvents)
      .catch(() => setError(t('adminAudit.loadFailed')))
      .finally(() => setLoading(false))
  }, [t])

  function actionLabel(action: AdminAuditEvent['action']) {
    return action === 'ROLE_CHANGED'
      ? t('adminAudit.roleChanged')
      : t('adminAudit.statusChanged')
  }

  function valueLabel(action: AdminAuditEvent['action'], value: string) {
    if (action === 'ROLE_CHANGED') {
      return value === 'ADMIN'
        ? t('adminUsers.adminRole')
        : t('adminUsers.userRole')
    }
    return value === 'ENABLED'
      ? t('adminUsers.enabled')
      : t('adminUsers.disabled')
  }

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">{t('adminAudit.eyebrow')}</p>
        <h1>{t('adminAudit.title')}</h1>
        <p className="muted">{t('adminAudit.help')}</p>
      </div>

      {error && (
        <div className="error" role="alert">
          {error}
        </div>
      )}

      {loading ? (
        <p className="muted">{t('common.loading')}</p>
      ) : events.length === 0 ? (
        <p className="muted">{t('adminAudit.empty')}</p>
      ) : (
        <div className="admin-users-scroll">
          <table className="admin-users-table">
            <thead>
              <tr>
                <th>{t('adminAudit.date')}</th>
                <th>{t('adminAudit.actor')}</th>
                <th>{t('adminAudit.action')}</th>
                <th>{t('adminAudit.target')}</th>
                <th>{t('adminAudit.previous')}</th>
                <th>{t('adminAudit.new')}</th>
              </tr>
            </thead>
            <tbody>
              {events.map((event) => (
                <tr key={event.id}>
                  <td data-label={t('adminAudit.date')}>
                    {new Intl.DateTimeFormat(locale, {
                      dateStyle: 'medium',
                      timeStyle: 'short',
                    }).format(new Date(event.createdAt))}
                  </td>
                  <td data-label={t('adminAudit.actor')}>
                    {event.actorUsername}
                  </td>
                  <td data-label={t('adminAudit.action')}>
                    {actionLabel(event.action)}
                  </td>
                  <td data-label={t('adminAudit.target')}>
                    {event.targetUsername}
                  </td>
                  <td data-label={t('adminAudit.previous')}>
                    {valueLabel(event.action, event.oldValue)}
                  </td>
                  <td data-label={t('adminAudit.new')}>
                    {valueLabel(event.action, event.newValue)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Layout>
  )
}
