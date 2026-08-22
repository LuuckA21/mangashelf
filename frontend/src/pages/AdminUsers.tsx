import { useEffect, useState } from 'react'
import {
  ApiError,
  adminAccounts,
  type AdminUser,
  type User,
} from '../api/client'
import { useSession } from '../api/session'
import Layout from '../components/Layout'
import { useI18n } from '../i18n'

interface Draft {
  role: User['role']
  enabled: boolean
}

export default function AdminUsers() {
  const { user: currentUser } = useSession()
  const { locale, t } = useI18n()
  const [users, setUsers] = useState<AdminUser[]>([])
  const [drafts, setDrafts] = useState<Record<number, Draft>>({})
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [savedUser, setSavedUser] = useState<number | null>(null)

  useEffect(() => {
    adminAccounts
      .list()
      .then((loaded) => {
        setUsers(loaded)
        setDrafts(
          Object.fromEntries(
            loaded.map((account) => [
              account.id,
              { role: account.role, enabled: account.enabled },
            ]),
          ),
        )
      })
      .catch(() => setError(t('adminUsers.loadFailed')))
      .finally(() => setLoading(false))
  }, [t])

  function changeDraft(id: number, update: Partial<Draft>) {
    setDrafts((current) => ({
      ...current,
      [id]: { ...current[id], ...update },
    }))
    setSavedUser(null)
  }

  async function save(account: AdminUser) {
    const draft = drafts[account.id]
    if (!draft) return
    setBusy(account.id)
    setError(null)
    setSavedUser(null)
    try {
      const updated = await adminAccounts.update(
        account.id,
        draft.role,
        draft.enabled,
      )
      setUsers((current) =>
        current.map((entry) => (entry.id === updated.id ? updated : entry)),
      )
      setDrafts((current) => ({
        ...current,
        [updated.id]: { role: updated.role, enabled: updated.enabled },
      }))
      setSavedUser(updated.id)
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? ({
              cannot_modify_self: t('adminUsers.cannotSelf'),
              last_admin_required: t('adminUsers.lastAdmin'),
            }[caught.code] ?? t('adminUsers.saveFailed'))
          : t('common.serverUnavailable'),
      )
    } finally {
      setBusy(null)
    }
  }

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">{t('adminUsers.eyebrow')}</p>
        <h1>{t('adminUsers.title')}</h1>
        <p className="muted">{t('adminUsers.help')}</p>
      </div>

      {error && (
        <div className="error" role="alert">
          {error}
        </div>
      )}
      {savedUser != null && (
        <div className="success" role="status">
          {t('adminUsers.saved')}
        </div>
      )}

      {loading ? (
        <p className="muted">{t('common.loading')}</p>
      ) : (
        <div className="admin-users-scroll">
          <table className="admin-users-table">
            <thead>
              <tr>
                <th>{t('adminUsers.username')}</th>
                <th>{t('adminUsers.email')}</th>
                <th>{t('adminUsers.role')}</th>
                <th>{t('adminUsers.status')}</th>
                <th>{t('adminUsers.language')}</th>
                <th>{t('adminUsers.created')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {users.map((account) => {
                const draft = drafts[account.id] ?? account
                const self = account.id === currentUser?.id
                const changed =
                  draft.role !== account.role ||
                  draft.enabled !== account.enabled
                return (
                  <tr key={account.id}>
                    <td data-label={t('adminUsers.username')}>
                      <strong>{account.username}</strong>
                      {self && (
                        <span className="self-badge">
                          {t('adminUsers.current')}
                        </span>
                      )}
                    </td>
                    <td data-label={t('adminUsers.email')}>{account.email}</td>
                    <td data-label={t('adminUsers.role')}>
                      <select
                        aria-label={`${t('adminUsers.role')}: ${account.username}`}
                        value={draft.role}
                        disabled={self || busy === account.id}
                        onChange={(event) =>
                          changeDraft(account.id, {
                            role: event.target.value as User['role'],
                          })
                        }
                      >
                        <option value="USER">{t('adminUsers.userRole')}</option>
                        <option value="ADMIN">
                          {t('adminUsers.adminRole')}
                        </option>
                      </select>
                    </td>
                    <td data-label={t('adminUsers.status')}>
                      <select
                        aria-label={`${t('adminUsers.status')}: ${account.username}`}
                        value={draft.enabled ? 'enabled' : 'disabled'}
                        disabled={self || busy === account.id}
                        onChange={(event) =>
                          changeDraft(account.id, {
                            enabled: event.target.value === 'enabled',
                          })
                        }
                      >
                        <option value="enabled">
                          {t('adminUsers.enabled')}
                        </option>
                        <option value="disabled">
                          {t('adminUsers.disabled')}
                        </option>
                      </select>
                    </td>
                    <td data-label={t('adminUsers.language')}>
                      {account.language.toUpperCase()}
                    </td>
                    <td data-label={t('adminUsers.created')}>
                      {new Intl.DateTimeFormat(locale, {
                        dateStyle: 'medium',
                      }).format(new Date(account.createdAt))}
                    </td>
                    <td className="admin-user-action">
                      <button
                        type="button"
                        className="quiet"
                        disabled={self || !changed || busy === account.id}
                        onClick={() => void save(account)}
                      >
                        {busy === account.id
                          ? t('common.saving')
                          : t('adminUsers.save')}
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </Layout>
  )
}
