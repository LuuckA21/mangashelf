import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  ApiError, catalog, purchases, type Manga, type PurchaseList, type Series,
} from '../../api/client'
import { parseAmount } from '../../format'
import { useI18n } from '../../i18n'

/** Adds a volume after searching the paginated catalogue and choosing an edition. */
export function PurchaseAddItem({ listId, onAdded, onError }: {
  listId: number
  onAdded: (list: PurchaseList) => void
  onError: (message: string) => void
}) {
  const { t } = useI18n()
  const [manga, setManga] = useState<Manga[]>([])
  const [mangaQuery, setMangaQuery] = useState('')
  const [activeMangaQuery, setActiveMangaQuery] = useState('')
  const [mangaPage, setMangaPage] = useState(0)
  const [mangaTotalPages, setMangaTotalPages] = useState(0)
  const [mangaTotalElements, setMangaTotalElements] = useState(0)
  const [mangaLoading, setMangaLoading] = useState(true)
  const [series, setSeries] = useState<Series[]>([])
  const [mangaId, setMangaId] = useState('')
  const [seriesId, setSeriesId] = useState('')
  const [number, setNumber] = useState('')
  const [date, setDate] = useState('')
  const [eur, setEur] = useState('')
  const [chf, setChf] = useState('')
  const [busy, setBusy] = useState(false)

  const loadManga = useCallback(async (query = '', targetPage = 0, append = false) => {
    setMangaLoading(true)
    if (!append) {
      setManga([])
      setMangaPage(0)
      setMangaTotalPages(0)
      setMangaTotalElements(0)
    }
    try {
      const result = await catalog.listManga(query, targetPage)
      setManga((current) => append
        ? [...new Map([...current, ...result.content].map((item) => [item.id, item])).values()]
        : result.content)
      setActiveMangaQuery(query.trim())
      setMangaPage(result.number)
      setMangaTotalPages(result.totalPages)
      setMangaTotalElements(result.totalElements)
    } catch {
      onError(t('purchase.catalogLoadFailed'))
    } finally {
      setMangaLoading(false)
    }
  }, [onError, t])

  useEffect(() => { void loadManga() }, [loadManga])

  useEffect(() => {
    setSeries([])
    setSeriesId('')
    if (mangaId) {
      catalog.listSeries(Number(mangaId)).then(setSeries).catch(() => undefined)
    }
  }, [mangaId])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      onAdded(await purchases.addItem(listId, {
        seriesId: Number(seriesId),
        volumeNumber: Number(number),
        releaseDate: date || null,
        priceEurCents: parseAmount(eur),
        priceChfCents: parseAmount(chf),
      }))
      setNumber('')
      setEur('')
      setChf('')
    } catch (error) {
      onError(error instanceof ApiError && error.code === 'item_already_on_list'
        ? t('purchase.itemDuplicate')
        : t('purchase.addFailed'))
    } finally {
      setBusy(false)
    }
  }

  function searchManga() {
    setMangaId('')
    void loadManga(mangaQuery)
  }

  return (
    <form className="panel" onSubmit={handleSubmit} style={{ marginTop: 32 }}>
      <p className="eyebrow" style={{ marginTop: 0 }}>{t('purchase.addVolume')}</p>

      <div className="field" style={{ marginBottom: 16 }}>
        <label htmlFor="pi-manga-search">{t('purchase.searchCatalog')}</label>
        <div className="row">
          <input
            id="pi-manga-search"
            placeholder={t('purchase.mangaTitle')}
            value={mangaQuery}
            onChange={(event) => setMangaQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                searchManga()
              }
            }}
            style={{ flex: 1, minWidth: 200 }}
          />
          <button type="button" className="quiet" disabled={mangaLoading}
                  onClick={searchManga}>
            {t('common.search')}
          </button>
        </div>
      </div>

      <div className="grid-2">
        <div className="field">
          <label htmlFor="pi-manga">Manga</label>
          <select id="pi-manga" value={mangaId} required
                  disabled={mangaLoading && manga.length === 0}
                  onChange={(event) => setMangaId(event.target.value)}>
            <option value="">{t('purchase.choose')}</option>
            {manga.map((item) => (
              <option key={item.id} value={item.id}>{item.displayTitle}</option>
            ))}
          </select>
          <div className="row" style={{ marginTop: 8 }}>
            <span className="muted" style={{ fontSize: 13 }}>
              {mangaLoading && manga.length === 0
                ? t('common.loading')
                : mangaTotalElements === 0
                  ? t('purchase.noManga')
                  : `${manga.length} ${t('collection.of')} ${mangaTotalElements}`}
            </span>
            {mangaPage + 1 < mangaTotalPages && (
              <button
                type="button"
                className="link-button"
                disabled={mangaLoading}
                onClick={() => void loadManga(activeMangaQuery, mangaPage + 1, true)}
              >
                {mangaLoading ? t('common.loading') : t('catalog.loadMore')}
              </button>
            )}
          </div>
        </div>
        <div className="field">
          <label htmlFor="pi-series">{t('purchase.chooseEdition')}</label>
          <select id="pi-series" value={seriesId} required disabled={series.length === 0}
                  onChange={(event) => setSeriesId(event.target.value)}>
            <option value="">{t('purchase.choose')}</option>
            {series.map((edition) => (
              <option key={edition.id} value={edition.id}>
                {edition.name} — {edition.publisher}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="row" style={{ alignItems: 'flex-end' }}>
        <div className="field-narrow">
          <label htmlFor="pi-number">{t('common.volume')}</label>
          <input id="pi-number" type="number" min={0} max={999} value={number} required
                 onChange={(event) => setNumber(event.target.value)} />
        </div>
        <div className="field-date">
          <label htmlFor="pi-date">{t('purchase.release')}</label>
          <input id="pi-date" type="date" value={date}
                 onChange={(event) => setDate(event.target.value)} />
        </div>
        <div className="field-medium">
          <label htmlFor="pi-eur">{t('purchase.priceEur')}</label>
          <input id="pi-eur" inputMode="decimal" placeholder="6.90"
                 value={eur} onChange={(event) => setEur(event.target.value)} />
        </div>
        <div className="field-medium">
          <label htmlFor="pi-chf">{t('purchase.priceChf')}</label>
          <input id="pi-chf" inputMode="decimal" placeholder="8.30"
                 value={chf} onChange={(event) => setChf(event.target.value)} />
        </div>
        <button type="submit" disabled={busy || !seriesId}>
          {busy ? t('purchase.adding') : t('purchase.addSuggested')}
        </button>
      </div>
    </form>
  )
}
