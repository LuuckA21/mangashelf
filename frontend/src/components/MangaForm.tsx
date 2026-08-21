import { useRef, useState, type FormEvent } from 'react'
import { catalog, type Manga } from '../api/client'
import { useI18n } from '../i18n'

interface Props {
  /** The work being edited, or null when creating a new one. */
  manga: Manga | null
  onSaved: (manga: Manga) => void
  onCancel?: () => void
  onError: (message: string) => void
}

/**
 * Every field of a work, for both creation and editing.
 *
 * <p>One component for both because the update endpoint replaces the whole
 * record: a shorter create form would produce rows whose missing fields then
 * have to be filled in a second, different form — and a field left out of an
 * update payload is written back as null.
 */
export default function MangaForm({ manga, onSaved, onCancel, onError }: Props) {
  const { t } = useI18n()
  const [titleRomaji, setTitleRomaji] = useState(manga?.titleRomaji ?? '')
  const [titleEnglish, setTitleEnglish] = useState(manga?.titleEnglish ?? '')
  const [titleNative, setTitleNative] = useState(manga?.titleNative ?? '')
  const [authors, setAuthors] = useState(manga?.authors ?? '')
  const [description, setDescription] = useState(manga?.description ?? '')
  const [coverUrl, setCoverUrl] = useState(manga?.coverUrl ?? '')
  const [status, setStatus] = useState<string>(manga?.status ?? '')
  const [genres, setGenres] = useState((manga?.genres ?? []).join(', '))
  const [startYear, setStartYear] = useState(
    manga?.startYear == null ? '' : String(manga.startYear))
  const [totalVolumes, setTotalVolumes] = useState(
    manga?.totalVolumes == null ? '' : String(manga.totalVolumes))

  const [busy, setBusy] = useState(false)
  const [uploading, setUploading] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      const body = {
        titleRomaji,
        titleEnglish: titleEnglish || null,
        titleNative: titleNative || null,
        authors: authors || null,
        description: description || null,
        coverUrl: coverUrl || null,
        status: (status || null) as Manga['status'],
        genres: genres.trim()
          ? genres.split(',').map((g) => g.trim()).filter(Boolean)
          : null,
        startYear: startYear === '' ? null : Number(startYear),
        totalVolumes: totalVolumes === '' ? null : Number(totalVolumes),
      }
      onSaved(manga
        ? await catalog.updateManga(manga.id, body)
        : await catalog.createManga(body))
    } catch {
      onError(t('common.saveFailed'))
    } finally {
      setBusy(false)
    }
  }

  /**
   * Uploads immediately rather than on submit: the file endpoint needs the
   * work to exist, so on a new work there is no id to send it to yet.
   */
  async function handleFile(file: File) {
    if (!manga) {
      onError(t('mangaForm.saveBeforeUpload'))
      return
    }
    setUploading(true)
    try {
      const updated = await catalog.uploadCover(manga.id, file)
      setCoverUrl(updated.coverUrl ?? '')
      onSaved(updated)
    } catch {
      onError(t('mangaForm.uploadFailed'))
    } finally {
      setUploading(false)
      if (fileInput.current) fileInput.current.value = ''
    }
  }

  return (
    <form className="panel" onSubmit={handleSubmit} style={{ marginBottom: 24 }}>
      <div className="grid-2">
        <div className="field">
          <label htmlFor="titleRomaji">{t('mangaForm.title')}</label>
          <input id="titleRomaji" value={titleRomaji} required autoFocus
                 onChange={(e) => setTitleRomaji(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="authorsEdit">{t('mangaForm.author')}</label>
          <input id="authorsEdit" value={authors}
                 onChange={(e) => setAuthors(e.target.value)} />
        </div>
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor="titleEnglishEdit">{t('mangaForm.english')}</label>
          <input id="titleEnglishEdit" value={titleEnglish}
                 onChange={(e) => setTitleEnglish(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="titleNative">{t('mangaForm.native')}</label>
          <input id="titleNative" value={titleNative}
                 onChange={(e) => setTitleNative(e.target.value)} />
        </div>
      </div>

      <div className="field">
        <label htmlFor="descriptionEdit">{t('mangaForm.plot')}</label>
        <textarea id="descriptionEdit" rows={5} value={description}
                  onChange={(e) => setDescription(e.target.value)} />
      </div>

      <div className="field">
        <label htmlFor="genresEdit">{t('mangaForm.genresSeparated')}</label>
        <input id="genresEdit" value={genres} placeholder="Action, Adventure"
               onChange={(e) => setGenres(e.target.value)} />
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor="statusEdit">{t('mangaForm.publicationStatus')}</label>
          <select id="statusEdit" value={status}
                  onChange={(e) => setStatus(e.target.value)}>
            <option value="">{t('mangaForm.notSpecified')}</option>
            <option value="RELEASING">{t('status.releasing')}</option>
            <option value="FINISHED">{t('status.finished')}</option>
            <option value="HIATUS">{t('status.hiatus')}</option>
            <option value="NOT_YET_RELEASED">{t('status.notYetReleased')}</option>
            <option value="CANCELLED">{t('status.cancelled')}</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="yearEdit">{t('mangaForm.startYear')}</label>
          <input id="yearEdit" type="number" min={1900} max={2200} value={startYear}
                 onChange={(e) => setStartYear(e.target.value)} />
        </div>
      </div>

      <div className="field">
        <label htmlFor="volsEdit">{t('mangaForm.originalVolumes')}</label>
        <input id="volsEdit" type="number" min={0} value={totalVolumes}
               placeholder={t('mangaForm.blankIfOngoing')}
               onChange={(e) => setTotalVolumes(e.target.value)} />
      </div>

      <div className="field">
        <label htmlFor="coverUrl">{t('mangaForm.cover')}</label>
        <div className="cover-picker">
          {coverUrl
            ? <img src={coverUrl} alt="" className="cover-preview" />
            : <div className="cover-preview empty-cover">{t('mangaForm.noCover')}</div>}

          <div className="cover-controls">
            <input
              id="coverUrl"
              placeholder={t('mangaForm.coverUrlPlaceholder')}
              value={coverUrl}
              onChange={(e) => setCoverUrl(e.target.value)}
            />
            <div className="row">
              <input
                ref={fileInput}
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                style={{ display: 'none' }}
                onChange={(e) => {
                  const file = e.target.files?.[0]
                  if (file) handleFile(file)
                }}
              />
              <button
                type="button"
                className="quiet"
                disabled={uploading}
                onClick={() => fileInput.current?.click()}
              >
                {uploading ? t('mangaForm.uploading') : t('mangaForm.upload')}
              </button>
              {coverUrl && (
                <button type="button" className="quiet" onClick={() => setCoverUrl('')}>
                  {t('mangaForm.removeCover')}
                </button>
              )}
            </div>
            <p className="muted" style={{ fontSize: 13, margin: 0 }}>
              {manga
                ? t('mangaForm.remoteCoverHelp')
                : t('mangaForm.saveFirst')}
            </p>
            <p className="muted" style={{ fontSize: 13, margin: '4px 0 0' }}>
              {t('mangaForm.uploadHelp')}
            </p>
          </div>
        </div>
      </div>

      <div className="inline-actions">
        <button type="submit" disabled={busy}>
          {busy ? t('common.saving') : manga ? t('common.save') : t('common.create')}
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
