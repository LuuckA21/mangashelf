import { useEffect, useId, useState, type ChangeEvent } from 'react'
import { t } from '../../i18n/t'

const MAX_BYTES = 5 * 1024 * 1024

/** File picker with a preview. The server validates again; this only saves a round trip. */
export default function CoverInput({
  onSelect,
}: {
  onSelect: (file: File) => void
}) {
  const id = useId()
  const [preview, setPreview] = useState<string>()
  const [error, setError] = useState<string>()

  // Revokes the previous URL when it changes, and the last one on unmount.
  useEffect(() => {
    if (preview) return () => URL.revokeObjectURL(preview)
  }, [preview])

  function change(ev: ChangeEvent<HTMLInputElement>) {
    const file = ev.target.files?.[0]
    if (!file) return
    if (file.size > MAX_BYTES) {
      setError(t('cover.tooBig'))
      return
    }
    setError(undefined)
    setPreview(URL.createObjectURL(file))
    onSelect(file)
  }

  return (
    <div className="field">
      <label htmlFor={id}>{t('cover.label')}</label>
      <input
        id={id}
        type="file"
        accept="image/jpeg,image/png"
        onChange={change}
        aria-describedby={`${id}-help`}
      />
      <p id={`${id}-help`} className="field-help">
        {t('cover.help')}
      </p>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {preview && (
        <img
          className="detail-cover"
          src={preview}
          alt={t('cover.preview')}
          width={120}
          height={180}
        />
      )}
    </div>
  )
}
