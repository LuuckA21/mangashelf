import { Link, useParams } from 'react-router'
import { t } from '../../i18n/t'
import { usePurchaseList } from './api'
import { monthLabel } from './monthLabel'

// Placeholder: the rows and totals arrive with the next task.
export default function PurchaseListPage() {
  const { id = '' } = useParams()
  const { data, isPending, isError } = usePurchaseList(id)
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
      <Link to="/purchases">{t('purchases.back')}</Link>
    </>
  )
}
