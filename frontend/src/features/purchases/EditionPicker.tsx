import { useState, type SyntheticEvent } from 'react'
import { t } from '../../i18n/t'
import { useSeriesDetail, useSeriesList } from '../catalog/api'

export type PickedEdition = {
  editionId: string
  label: string
  volumeCount: number
}

/** Search a series, then pick one of its editions. */
export default function EditionPicker({
  onPick,
}: {
  onPick: (e: PickedEdition) => void
}) {
  const [text, setText] = useState('')
  const [q, setQ] = useState('')
  const [seriesId, setSeriesId] = useState('')
  const list = useSeriesList(q, 1)
  const detail = useSeriesDetail(seriesId)

  function search(ev: SyntheticEvent) {
    ev.preventDefault()
    setSeriesId('')
    setQ(text.trim())
  }

  return (
    <div className="edition-picker">
      <form className="search-form" onSubmit={search} role="search">
        <div className="field">
          <label htmlFor="picker-q">{t('purchases.searchSeries')}</label>
          <input
            id="picker-q"
            value={text}
            onChange={(e) => setText(e.target.value)}
          />
        </div>
        <button type="submit" className="quiet">
          {t('purchases.search')}
        </button>
      </form>
      {q && !seriesId && (
        <ul className="chips">
          {list.data?.content.map((s) => (
            <li key={s.id}>
              <button
                type="button"
                className="chip"
                onClick={() => setSeriesId(s.id)}
              >
                {s.title}
              </button>
            </li>
          ))}
        </ul>
      )}
      {seriesId && detail.data && (
        <>
          <p className="muted">
            {t('purchases.pickEdition', { series: detail.data.title })}
          </p>
          <ul className="chips">
            {detail.data.editions.map((e) => (
              <li key={e.id}>
                <button
                  type="button"
                  className="chip"
                  onClick={() =>
                    onPick({
                      editionId: e.id,
                      label: `${detail.data.title} · ${e.name}`,
                      volumeCount: e.volumeCount,
                    })
                  }
                >
                  {e.name}
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
