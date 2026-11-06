import { useEffect, useId, useState, type SyntheticEvent } from 'react'
import { ApiError, fieldMessage } from '../../api/client'
import ConfirmDelete from '../../components/ConfirmDelete'
import type { Publisher } from '../../api/types'
import { t } from '../../i18n/t'
import { usePublisherMutations, usePublishers } from './api'

/** Shows the server's field message under the input, or the detail above. */
function useNameError(error: unknown, inputId: string) {
  const msg = fieldMessage(error, 'name')
  const general =
    error instanceof ApiError
      ? error.errors.length === 0
        ? error.detail
        : ''
      : error
        ? t('error.generic')
        : ''
  useEffect(() => {
    if (msg) document.getElementById(inputId)?.focus()
  }, [msg, error, inputId])
  return { msg, general }
}

function NameField({
  id,
  label,
  value,
  onChange,
  msg,
}: {
  id: string
  label: string
  value: string
  onChange: (v: string) => void
  msg?: string
}) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        required
        maxLength={100}
        autoComplete="off"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={msg ? true : undefined}
        aria-describedby={msg ? `${id}-error` : undefined}
      />
      {msg && (
        <p id={`${id}-error`} className="field-help">
          {msg}
        </p>
      )}
    </div>
  )
}

function AddForm({ onStart }: { onStart: () => void }) {
  const { create } = usePublisherMutations()
  const [name, setName] = useState('')
  const id = useId()
  const { msg, general } = useNameError(create.error, id)

  async function submit(e: SyntheticEvent) {
    e.preventDefault()
    onStart()
    try {
      await create.mutateAsync(name)
      setName('')
    } catch {
      // shown through create.error
    }
  }

  return (
    <form onSubmit={submit}>
      {general && (
        <p className="error" role="alert">
          {general}
        </p>
      )}
      <NameField
        id={id}
        label={t('publishers.name')}
        value={name}
        onChange={setName}
        msg={msg}
      />
      <button type="submit" disabled={create.isPending}>
        {t('publishers.add')}
      </button>
    </form>
  )
}

function PublisherRow({
  publisher,
  onError,
}: {
  publisher: Publisher
  onError: (e: unknown) => void
}) {
  const { rename, remove } = usePublisherMutations()
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(publisher.name)
  const id = useId()
  const { msg, general } = useNameError(rename.error, id)

  async function submit(e: SyntheticEvent) {
    e.preventDefault()
    try {
      await rename.mutateAsync({ id: publisher.id, name })
      setEditing(false)
    } catch {
      // shown through rename.error
    }
  }

  if (editing)
    return (
      <li className="edition-row">
        <form className="grow" onSubmit={submit}>
          {general && (
            <p className="error" role="alert">
              {general}
            </p>
          )}
          <NameField
            id={id}
            label={t('publishers.newName', { name: publisher.name })}
            value={name}
            onChange={setName}
            msg={msg}
          />
          <div className="inline-actions">
            <button type="submit" disabled={rename.isPending}>
              {t('common.save')}
            </button>
            <button
              type="button"
              className="quiet"
              onClick={() => setEditing(false)}
            >
              {t('common.cancel')}
            </button>
          </div>
        </form>
      </li>
    )

  return (
    <li className="edition-row">
      <span className="grow name">{publisher.name}</span>
      <span className="inline-actions">
        <button
          className="quiet"
          aria-label={t('publishers.renameLabel', { name: publisher.name })}
          onClick={() => {
            rename.reset()
            onError(null)
            setName(publisher.name)
            setEditing(true)
          }}
        >
          {t('publishers.rename')}
        </button>
        <ConfirmDelete
          what={t('publishers.deleteWhat', { name: publisher.name })}
          label={t('common.delete')}
          ariaLabel={t('publishers.deleteLabel', { name: publisher.name })}
          onCancel={() => onError(null)}
          onConfirm={() => {
            onError(null)
            return remove.mutateAsync(publisher.id).then(() => {}, onError)
          }}
        />
      </span>
    </li>
  )
}

export default function PublishersSection() {
  const { data, isPending, isError, refetch } = usePublishers()
  // One slot for delete failures (409 publisher in use).
  const [error, setError] = useState<unknown>(null)
  const message =
    error instanceof ApiError ? error.detail : error ? t('error.generic') : ''

  return (
    <section className="panel" aria-labelledby="publishers-title">
      <h2 id="publishers-title">{t('publishers.title')}</h2>
      <AddForm onStart={() => setError(null)} />
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
      {data?.length === 0 && <p className="muted">{t('publishers.empty')}</p>}
      {data && data.length > 0 && (
        <ul className="edition-list" aria-label={t('publishers.list')}>
          {data.map((p) => (
            <PublisherRow key={p.id} publisher={p} onError={setError} />
          ))}
        </ul>
      )}
    </section>
  )
}
