import { useEffect, useState } from 'react'
import {
  purchases,
  type PurchaseList,
  type PurchaseSuggestion,
} from '../../api/client'
import { formatCents } from '../../format'
import { useI18n } from '../../i18n'

const VISIBLE = 5

/** Next volumes of previously purchased runs, addable with one click. */
export default function Suggestions({
  listId,
  itemCount,
  onAdded,
  onError,
}: {
  listId: number
  itemCount: number
  onAdded: (list: PurchaseList) => void
  onError: (message: string) => void
}) {
  const { locale, t } = useI18n()
  const [rows, setRows] = useState<PurchaseSuggestion[]>([])
  const [query, setQuery] = useState('')
  const [expanded, setExpanded] = useState(false)
  const [busy, setBusy] = useState<number | null>(null)

  useEffect(() => {
    purchases
      .suggestions(listId)
      .then(setRows)
      .catch(() => undefined)
  }, [listId, itemCount])

  if (rows.length === 0) return null

  const needle = query.trim().toLowerCase()
  const matching = needle
    ? rows.filter(
        (row) =>
          row.mangaTitle.toLowerCase().includes(needle) ||
          row.seriesName.toLowerCase().includes(needle) ||
          row.publisher.toLowerCase().includes(needle),
      )
    : rows
  const visible = expanded || needle ? matching : matching.slice(0, VISIBLE)
  const hidden = matching.length - visible.length

  return (
    <div className="panel" style={{ marginTop: 32 }}>
      <div className="row" style={{ marginBottom: 12 }}>
        <p className="eyebrow" style={{ margin: 0 }}>
          {t('purchase.continueSeries')} · {rows.length}
        </p>
        <span className="spacer" />
        <input
          aria-label={t('purchase.filterSuggestions')}
          placeholder={t('purchase.filter')}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          className="filter-input"
        />
      </div>

      {matching.length === 0 ? (
        <p className="muted" style={{ fontSize: 14, marginBottom: 0 }}>
          {t('purchase.noSeries')} “{query}”.
        </p>
      ) : (
        <>
          <ul className="suggestion-list">
            {visible.map((row) => (
              <li key={row.seriesId}>
                <div className="grow">
                  <span className="suggestion-title">{row.mangaTitle}</span>
                  <span className="muted">
                    {' '}
                    · {row.seriesName} · {t('common.volume')} {row.volumeNumber}
                  </span>
                  <div className="muted" style={{ fontSize: 13 }}>
                    {[
                      row.priceChfCents != null
                        ? `CHF ${formatCents(row.priceChfCents, locale)}`
                        : null,
                      row.priceEurCents != null
                        ? `EUR ${formatCents(row.priceEurCents, locale)}`
                        : null,
                      `${t('purchase.likeIn')} “${row.lastBoughtIn}”`,
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                  </div>
                </div>
                <button
                  className="quiet"
                  disabled={busy !== null}
                  onClick={async () => {
                    setBusy(row.seriesId)
                    try {
                      onAdded(
                        await purchases.addItem(listId, {
                          seriesId: row.seriesId,
                          volumeNumber: row.volumeNumber,
                          priceEurCents: row.priceEurCents,
                          priceChfCents: row.priceChfCents,
                        }),
                      )
                    } catch {
                      onError(t('purchase.addFailed'))
                    } finally {
                      setBusy(null)
                    }
                  }}
                >
                  {busy === row.seriesId
                    ? t('purchase.adding')
                    : `${t('purchase.addVolumeShort')} ${row.volumeNumber}`}
                </button>
              </li>
            ))}
          </ul>

          {hidden > 0 && (
            <button
              type="button"
              className="quiet"
              style={{ marginTop: 12 }}
              onClick={() => setExpanded(true)}
            >
              {t('purchase.showOthers')} {hidden}
            </button>
          )}
          {expanded && !needle && (
            <button
              type="button"
              className="quiet"
              style={{ marginTop: 12 }}
              onClick={() => setExpanded(false)}
            >
              {t('purchase.showLess')}
            </button>
          )}
        </>
      )}
    </div>
  )
}
