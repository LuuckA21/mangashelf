import { useState } from 'react'
import { Link, useParams } from 'react-router'
import type { ItemView } from '../../api/types'
import ConfirmDelete from '../../components/ConfirmDelete'
import { t } from '../../i18n/t'
import { usePurchaseList, useListChange, usePurchaseSettings } from './api'
import ItemForm from './ItemForm'
import { formatChf, formatEur } from './money'
import { monthLabel } from './monthLabel'
import Totals from './Totals'

const dayFmt = new Intl.DateTimeFormat('it-IT', {
  day: 'numeric',
  month: 'long',
})
const dayLabel = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number)
  return dayFmt.format(new Date(y, m - 1, d))
}

const byName = (a: ItemView, b: ItemView) =>
  a.seriesTitle.localeCompare(b.seriesTitle) ||
  a.editionName.localeCompare(b.editionName) ||
  a.volume - b.volume

/** Groups ascending by date; the undated group (key "") goes last. */
function groupByDate(items: ItemView[]) {
  const groups = new Map<string, ItemView[]>()
  for (const i of items) {
    const k = i.releaseDate ?? ''
    groups.set(k, [...(groups.get(k) ?? []), i])
  }
  return [...groups.entries()]
    .sort(([a], [b]) => (a === '' ? 1 : b === '' ? -1 : a.localeCompare(b)))
    .map(([date, rows]) => ({ date, rows: rows.sort(byName) }))
}

function Row({
  listId,
  item,
  settings,
}: {
  listId: string
  item: ItemView
  settings: { chfRate: number; discountPercent: number }
}) {
  const change = useListChange(listId)
  const [editing, setEditing] = useState(false)
  const name = `${item.seriesTitle} · ${item.editionName}`
  const volume =
    item.editionVolumeCount === 1
      ? t('purchases.single')
      : t('purchases.volumeShort', { volume: item.volume })

  if (editing)
    return (
      <li className="edition-row item-row">
        <div className="grow">
          <strong>{name}</strong>
          <ItemForm
            listId={listId}
            settings={settings}
            item={item}
            onDone={() => setEditing(false)}
          />
        </div>
      </li>
    )

  return (
    <li className={`edition-row item-row${item.booked ? ' booked' : ''}`}>
      <div className="grow">
        <span className="name">{item.seriesTitle}</span>{' '}
        <span className="muted">{item.editionName}</span>
        <div>{volume}</div>
        {item.alsoIn.map((l) => (
          <div key={l} className="muted">
            {t('purchases.alsoIn', { list: l })}
          </div>
        ))}
      </div>
      <span className="num">{formatEur(item.priceEurCents)}</span>
      <span className="num">{formatChf(item.priceChfCents)}</span>
      <div className="inline-actions">
        <button
          type="button"
          className="quiet"
          aria-pressed={item.booked}
          disabled={change.isPending}
          onClick={() =>
            change.mutate({
              method: 'PATCH',
              path: `/items/${item.id}`,
              body: { booked: !item.booked },
            })
          }
        >
          {t('purchases.booked')}
        </button>
        <button
          type="button"
          className="quiet"
          onClick={() => setEditing(true)}
        >
          {t('purchases.editRow')}
        </button>
        <ConfirmDelete
          what={t('purchases.rowDeleteWhat', {
            name: `${name} ${volume}`,
          })}
          onConfirm={async () => {
            await change.mutateAsync({
              method: 'DELETE',
              path: `/items/${item.id}`,
            })
          }}
        />
      </div>
    </li>
  )
}

export default function PurchaseListPage() {
  const { id = '' } = useParams()
  const { data, isPending, isError } = usePurchaseList(id)
  const settings = usePurchaseSettings().data
  return (
    <>
      <div className="page-head">
        <h1>
          {data
            ? t('purchases.listTitle', {
                description: data.description,
                month: monthLabel(data.month, data.year),
              })
            : t('purchases.title')}
        </h1>
      </div>
      {isPending && <p className="muted">{t('common.loading')}</p>}
      {isError && (
        <p className="error" role="alert">
          {t('error.generic')}
        </p>
      )}
      {data && settings && (
        <>
          <section className="panel">
            <h2>{t('purchases.addTitle')}</h2>
            <ItemForm listId={id} settings={settings} onDone={() => {}} />
          </section>
          {data.items.length === 0 && (
            <p className="empty">{t('purchases.noRows')}</p>
          )}
          {groupByDate(data.items).map((g) => (
            <section key={g.date} className="item-group">
              <h2>{g.date ? dayLabel(g.date) : t('purchases.undated')}</h2>
              <ul className="edition-list">
                {g.rows.map((i) => (
                  <Row key={i.id} listId={id} item={i} settings={settings} />
                ))}
              </ul>
            </section>
          ))}
          <Totals list={data} settings={settings} />
        </>
      )}
      <Link to="/purchases">{t('purchases.back')}</Link>
    </>
  )
}
