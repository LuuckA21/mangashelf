import { useState } from 'react'
import { ApiError } from '../../api/client'
import { t } from '../../i18n/t'
import ConfirmDelete from '../../components/ConfirmDelete'
import { useMe } from '../auth/useMe'
import type { Role } from '../../api/types'
import { useUserMutations, useUsers } from './api'
import CreateUserForm from './CreateUserForm'
import ResetPasswordForm from './ResetPasswordForm'

export default function UsersPage() {
  const { data: me } = useMe()
  const [page, setPage] = useState(1)
  const { data, isPending, isError, refetch } = useUsers(page)
  const { changeRole, remove } = useUserMutations()
  // One slot for role and delete failures (409 last admin and the like).
  const [error, setError] = useState<unknown>(null)
  const message =
    error instanceof ApiError ? error.detail : error ? t('error.generic') : ''

  return (
    <>
      <div className="page-head">
        <h1>{t('users.title')}</h1>
      </div>

      {message && (
        <p className="error" role="alert">
          {message}
        </p>
      )}
      {isPending && <p className="muted">{t('common.loading')}</p>}
      {isError && (
        <div className="error" role="alert">
          {t('error.generic')}{' '}
          <button className="quiet" onClick={() => refetch()}>
            {t('common.retry')}
          </button>
        </div>
      )}
      {data && (
        <div className="admin-users-scroll">
          <table className="admin-users-table" aria-label={t('users.list')}>
            <thead>
              <tr>
                <th>{t('users.colUsername')}</th>
                <th>{t('users.colRole')}</th>
                <th>{t('users.colCreated')}</th>
                <th>{t('users.colActions')}</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((u) => {
                const self = u.id === me?.id
                return (
                  <tr key={u.id}>
                    <td data-label={t('users.colUsername')}>
                      {u.username}
                      {self && (
                        <span className="self-badge">{t('users.you')}</span>
                      )}
                    </td>
                    <td data-label={t('users.colRole')}>
                      {self ? (
                        t(`role.${u.role}`)
                      ) : (
                        <select
                          aria-label={t('users.roleOf', {
                            username: u.username,
                          })}
                          value={u.role}
                          disabled={changeRole.isPending}
                          onChange={(e) =>
                            changeRole.mutate(
                              { id: u.id, role: e.target.value as Role },
                              {
                                onSuccess: () => setError(null),
                                onError: setError,
                              },
                            )
                          }
                        >
                          {(['USER', 'ADMIN'] as const).map((r) => (
                            <option key={r} value={r}>
                              {t(`role.${r}`)}
                            </option>
                          ))}
                        </select>
                      )}
                    </td>
                    <td data-label={t('users.colCreated')}>
                      {new Date(u.createdAt).toLocaleDateString('it-IT')}
                    </td>
                    <td
                      data-label={t('users.colActions')}
                      className="admin-user-action"
                    >
                      {!self && (
                        <>
                          <ResetPasswordForm id={u.id} username={u.username} />{' '}
                          <ConfirmDelete
                            what={t('users.deleteWhat', {
                              username: u.username,
                            })}
                            onConfirm={() =>
                              remove
                                .mutateAsync(u.id)
                                .then(() => setError(null), setError)
                            }
                          />
                        </>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {data && data.page.totalPages > 1 && (
        <nav className="inline-actions" aria-label={t('collection.pagination')}>
          <button
            className="quiet"
            disabled={page <= 1}
            onClick={() => setPage(page - 1)}
          >
            {t('collection.prev')}
          </button>
          <span>
            {t('collection.pageOf', { page, total: data.page.totalPages })}
          </span>
          <button
            className="quiet"
            disabled={page >= data.page.totalPages}
            onClick={() => setPage(page + 1)}
          >
            {t('collection.next')}
          </button>
        </nav>
      )}

      <section className="panel settings-panel">
        <h2>{t('users.createTitle')}</h2>
        <CreateUserForm />
      </section>
    </>
  )
}
