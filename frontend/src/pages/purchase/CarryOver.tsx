import { useEffect, useState } from 'react'
import {
  purchases,
  type PurchaseList,
  type PurchaseListSummary,
} from '../../api/client'
import { useI18n } from '../../i18n'

/** Moves unpurchased rows from an earlier list into this one. */
export default function CarryOver({
  list,
  onMoved,
  onError,
}: {
  list: PurchaseList
  onMoved: (moved: number) => Promise<void>
  onError: (message: string) => void
}) {
  const { t } = useI18n()
  const [others, setOthers] = useState<PurchaseListSummary[]>([])
  const [sourceId, setSourceId] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    purchases
      .listAll()
      .then((all) =>
        setOthers(
          all.filter(
            (candidate) =>
              candidate.id !== list.id &&
              candidate.itemCount > candidate.purchasedCount,
          ),
        ),
      )
      .catch(() => undefined)
  }, [list.id, list.items.length])

  if (others.length === 0) return null

  return (
    <div className="panel" style={{ marginTop: 32 }}>
      <p className="eyebrow" style={{ marginTop: 0 }}>
        {t('purchase.carryTitle')}
      </p>
      <div className="row">
        <select
          aria-label={t('purchase.sourceList')}
          value={sourceId}
          onChange={(event) => setSourceId(event.target.value)}
          style={{ flex: 1, minWidth: 200 }}
        >
          <option value="">{t('purchase.chooseList')}</option>
          {others.map((candidate) => (
            <option key={candidate.id} value={candidate.id}>
              {candidate.name} —{' '}
              {candidate.itemCount - candidate.purchasedCount}{' '}
              {t('purchase.unpurchased')}
            </option>
          ))}
        </select>
        <button
          disabled={!sourceId || busy}
          onClick={async () => {
            setBusy(true)
            try {
              const { moved } = await purchases.carryOver(
                list.id,
                Number(sourceId),
              )
              await onMoved(moved)
              setSourceId('')
            } catch {
              onError(t('purchase.carryFailed'))
            } finally {
              setBusy(false)
            }
          }}
        >
          {busy ? t('purchase.carrying') : t('purchase.carry')}
        </button>
      </div>
      <p className="muted" style={{ fontSize: 13, marginBottom: 0 }}>
        {t('purchase.carryHelp')}
      </p>
    </div>
  )
}
