import { useRef, useState, type FormEvent } from 'react'
import { catalog, type Manga } from '../api/client'

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
      onError('Salvataggio non riuscito.')
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
      onError('Salva prima l’opera, poi potrai caricare la copertina.')
      return
    }
    setUploading(true)
    try {
      const updated = await catalog.uploadCover(manga.id, file)
      setCoverUrl(updated.coverUrl ?? '')
      onSaved(updated)
    } catch {
      onError('Caricamento della copertina non riuscito.')
    } finally {
      setUploading(false)
      if (fileInput.current) fileInput.current.value = ''
    }
  }

  return (
    <form className="panel" onSubmit={handleSubmit} style={{ marginBottom: 24 }}>
      <div className="grid-2">
        <div className="field">
          <label htmlFor="titleRomaji">Titolo</label>
          <input id="titleRomaji" value={titleRomaji} required autoFocus
                 onChange={(e) => setTitleRomaji(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="authorsEdit">Autore</label>
          <input id="authorsEdit" value={authors}
                 onChange={(e) => setAuthors(e.target.value)} />
        </div>
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor="titleEnglishEdit">Titolo inglese</label>
          <input id="titleEnglishEdit" value={titleEnglish}
                 onChange={(e) => setTitleEnglish(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="titleNative">Titolo originale</label>
          <input id="titleNative" value={titleNative}
                 onChange={(e) => setTitleNative(e.target.value)} />
        </div>
      </div>

      <div className="field">
        <label htmlFor="descriptionEdit">Trama</label>
        <textarea id="descriptionEdit" rows={5} value={description}
                  onChange={(e) => setDescription(e.target.value)} />
      </div>

      <div className="field">
        <label htmlFor="genresEdit">Generi (separati da virgola)</label>
        <input id="genresEdit" value={genres} placeholder="Action, Adventure"
               onChange={(e) => setGenres(e.target.value)} />
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor="statusEdit">Stato pubblicazione</label>
          <select id="statusEdit" value={status}
                  onChange={(e) => setStatus(e.target.value)}>
            <option value="">Non indicato</option>
            <option value="RELEASING">In corso</option>
            <option value="FINISHED">Conclusa</option>
            <option value="HIATUS">In pausa</option>
            <option value="NOT_YET_RELEASED">Non ancora uscita</option>
            <option value="CANCELLED">Cancellata</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="yearEdit">Anno di inizio</label>
          <input id="yearEdit" type="number" min={1900} max={2200} value={startYear}
                 onChange={(e) => setStartYear(e.target.value)} />
        </div>
      </div>

      <div className="field">
        <label htmlFor="volsEdit">Volumi originali (edizione giapponese)</label>
        <input id="volsEdit" type="number" min={0} value={totalVolumes}
               placeholder="vuoto se ancora in corso"
               onChange={(e) => setTotalVolumes(e.target.value)} />
      </div>

      <div className="field">
        <label>Copertina</label>
        <div className="cover-picker">
          {coverUrl
            ? <img src={coverUrl} alt="" className="cover-preview" />
            : <div className="cover-preview empty-cover">nessuna</div>}

          <div className="cover-controls">
            <input
              placeholder="Incolla l’indirizzo di un’immagine"
              value={coverUrl}
              onChange={(e) => setCoverUrl(e.target.value)}
            />
            <div className="row">
              <input
                ref={fileInput}
                type="file"
                accept="image/*"
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
                {uploading ? 'Carico…' : 'Carica un file'}
              </button>
              {coverUrl && (
                <button type="button" className="quiet" onClick={() => setCoverUrl('')}>
                  Togli
                </button>
              )}
            </div>
            <p className="muted" style={{ fontSize: 13, margin: 0 }}>
              {manga
                ? 'Un indirizzo incollato viene scaricato sul server al salvataggio.'
                : 'Salva l’opera per poter caricare un file dal computer.'}
            </p>
          </div>
        </div>
      </div>

      <div className="inline-actions">
        <button type="submit" disabled={busy}>
          {busy ? 'Salvo…' : manga ? 'Salva' : 'Crea'}
        </button>
        {onCancel && (
          <button type="button" className="quiet" onClick={onCancel}>Annulla</button>
        )}
      </div>
    </form>
  )
}
