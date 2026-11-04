import {
  useEffect,
  useId,
  useState,
  type ReactNode,
  type SyntheticEvent,
} from 'react'
import { ApiError } from '../../api/client'
import type { EditionStatus, EditionType } from '../../api/types'
import { t } from '../../i18n/t'

export type EditionFormValues = {
  name: string
  publisher: string
  type: EditionType
  status: EditionStatus
  volumeCount: number
}

const TYPES: EditionType[] = ['REGULAR', 'VARIANT', 'JAPANESE']
const STATUSES: EditionStatus[] = ['ONGOING', 'COMPLETED']
const FIELDS = ['name', 'publisher', 'type', 'status', 'volumeCount'] as const

/** Used to create and to edit. The caller owns the request and passes its error back. */
export default function EditionForm({
  initial,
  submitLabel,
  pending,
  error,
  onSubmit,
  onCancel,
  conflictNote,
  children,
}: {
  initial?: Partial<EditionFormValues>
  submitLabel: string
  pending: boolean
  error: unknown
  onSubmit: (values: EditionFormValues) => void
  onCancel?: () => void
  /** Shown after the server's message when it answers 409 (stale data). */
  conflictNote?: string
  /** Extra controls placed before the submit button. */
  children?: ReactNode
}) {
  const prefix = useId()
  const [name, setName] = useState(initial?.name ?? '')
  const [publisher, setPublisher] = useState(initial?.publisher ?? '')
  const [type, setType] = useState<EditionType>(initial?.type ?? 'REGULAR')
  const [status, setStatus] = useState<EditionStatus>(
    initial?.status ?? 'ONGOING',
  )
  const [volumeCount, setVolumeCount] = useState(
    String(initial?.volumeCount ?? 1),
  )

  const fieldErrors = error instanceof ApiError ? error.errors : []
  const message = (f: string) => fieldErrors.find((e) => e.field === f)?.message
  // Errors for fields this form does not show (e.g. `version`) must not vanish.
  const unmatched = fieldErrors.filter(
    (e) => !(FIELDS as readonly string[]).includes(e.field),
  )
  const general =
    error instanceof ApiError
      ? fieldErrors.length === 0
        ? error.detail
        : unmatched.map((e) => e.message).join(' ')
      : error !== null && error !== undefined && t('error.generic')

  // Focus the first invalid field once its error is rendered, so it is read out.
  const firstInvalid = FIELDS.find(message)
  useEffect(() => {
    if (firstInvalid)
      document.getElementById(`${prefix}-${firstInvalid}`)?.focus()
  }, [firstInvalid, error, prefix])

  function submit(ev: SyntheticEvent) {
    ev.preventDefault()
    onSubmit({
      name: name.trim(),
      publisher,
      type,
      status,
      volumeCount: Number(volumeCount),
    })
  }

  function field(
    f: (typeof FIELDS)[number],
    label: string,
    control: ReactNode,
  ) {
    return (
      <div className="field">
        <label htmlFor={`${prefix}-${f}`}>{label}</label>
        {control}
        {message(f) && (
          <p id={`${prefix}-${f}-error`} className="field-help">
            {message(f)}
          </p>
        )}
      </div>
    )
  }

  const aria = (f: (typeof FIELDS)[number]) => ({
    id: `${prefix}-${f}`,
    'aria-invalid': message(f) ? (true as const) : undefined,
    'aria-describedby': message(f) ? `${prefix}-${f}-error` : undefined,
  })

  return (
    <form onSubmit={submit}>
      {general && (
        <p className="error" role="alert">
          {general}
          {error instanceof ApiError &&
            error.status === 409 &&
            conflictNote && <> {conflictNote}</>}
        </p>
      )}
      {field(
        'name',
        t('edition.nameLabel'),
        <input
          {...aria('name')}
          required
          maxLength={200}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />,
      )}
      {field(
        'publisher',
        t('edition.publisherLabel'),
        <input
          {...aria('publisher')}
          maxLength={200}
          value={publisher}
          onChange={(e) => setPublisher(e.target.value)}
        />,
      )}
      {field(
        'type',
        t('edition.typeLabel'),
        <select
          {...aria('type')}
          value={type}
          onChange={(e) => setType(e.target.value as EditionType)}
        >
          {TYPES.map((x) => (
            <option key={x} value={x}>
              {t(`type.${x}`)}
            </option>
          ))}
        </select>,
      )}
      {field(
        'status',
        t('edition.statusLabel'),
        <select
          {...aria('status')}
          value={status}
          onChange={(e) => setStatus(e.target.value as EditionStatus)}
        >
          {STATUSES.map((x) => (
            <option key={x} value={x}>
              {t(`status.${x}`)}
            </option>
          ))}
        </select>,
      )}
      {field(
        'volumeCount',
        t('edition.volumeCountLabel'),
        <input
          {...aria('volumeCount')}
          className="field-narrow"
          type="number"
          required
          min={1}
          max={9999}
          step={1}
          value={volumeCount}
          onChange={(e) => setVolumeCount(e.target.value)}
        />,
      )}
      {children}
      <div className="inline-actions">
        <button type="submit" disabled={pending}>
          {submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="quiet" onClick={onCancel}>
            {t('common.cancel')}
          </button>
        )}
      </div>
    </form>
  )
}
