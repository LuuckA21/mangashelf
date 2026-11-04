import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import ConfirmDelete from '../../components/ConfirmDelete'
import type { EditionResponse } from '../../api/types'
import { t } from '../../i18n/t'
import { useAddToCollection } from '../collection/api'
import { useMe } from '../auth/useMe'
import {
  errorText,
  useCreateEdition,
  useDeleteSeries,
  useRenameSeries,
  useSeriesDetail,
} from './api'
import CoverInput from './CoverInput'
import EditionForm from './EditionForm'
import SeriesForm from './SeriesForm'

function EditionRow({ e }: { e: EditionResponse }) {
  const add = useAddToCollection(e.id)
  return (
    <li className="edition-row">
      <div className="grow">
        <Link className="name" to={`/editions/${e.id}`}>
          {e.name}
        </Link>
        <div className="muted">
          {[
            e.publisher,
            t(`type.${e.type}`),
            t(`status.${e.status}`),
            `${t('edition.volumes')} ${e.volumeCount}`,
          ]
            .filter(Boolean)
            .join(' · ')}
        </div>
        {add.isError && (
          <p className="error" role="alert">
            {t('error.generic')}
          </p>
        )}
      </div>
      {e.inMyCollection ? (
        <span className="muted">{t('series.inCollection')}</span>
      ) : (
        <button
          onClick={() => add.mutate()}
          disabled={add.isPending}
          aria-label={`${t('edition.add')}: ${e.name}`}
        >
          {t('edition.add')}
        </button>
      )}
    </li>
  )
}

export default function SeriesPage() {
  const id = useParams().id ?? ''
  const navigate = useNavigate()
  const me = useMe()
  const isAdmin = me.data?.role === 'ADMIN'
  const { data, isPending, isError, refetch } = useSeriesDetail(id)
  const rename = useRenameSeries(id)
  const remove = useDeleteSeries(id)
  const createEdition = useCreateEdition(id)
  const [renaming, setRenaming] = useState(false)
  const [cover, setCover] = useState<File | null>(null)

  if (isPending) return <p className="muted">{t('common.loading')}</p>
  if (isError)
    return (
      <div className="error" role="alert">
        {t('error.generic')}{' '}
        <button className="quiet" onClick={() => refetch()}>
          {t('common.retry')}
        </button>
      </div>
    )

  return (
    <>
      <div className="page-head">
        <h1>{data.title}</h1>
        {isAdmin && !renaming && (
          <div className="inline-actions">
            <button className="quiet" onClick={() => setRenaming(true)}>
              {t('series.rename')}
            </button>
            <ConfirmDelete
              label={t('series.delete')}
              what={t('series.deleteWhat')}
              onConfirm={() =>
                remove.mutateAsync().then(
                  () => navigate('/catalog'),
                  () => undefined,
                )
              }
            />
          </div>
        )}
        {remove.error && (
          <p className="error" role="alert">
            {errorText(remove.error)}
          </p>
        )}
        {renaming && (
          <SeriesForm
            initial={data.title}
            submitLabel={t('common.save')}
            onCancel={() => setRenaming(false)}
            onSubmit={async (title) => {
              await rename.mutateAsync(title)
              setRenaming(false)
            }}
          />
        )}
      </div>

      <h2>{t('series.editions')}</h2>
      {data.editions.length === 0 ? (
        <p className="empty">{t('series.noEditions')}</p>
      ) : (
        <ul className="edition-list">
          {data.editions.map((e) => (
            <EditionRow key={e.id} e={e} />
          ))}
        </ul>
      )}

      <section className="panel settings-panel">
        <h2>{t('edition.createTitle')}</h2>
        <EditionForm
          submitLabel={t('edition.create')}
          pending={createEdition.isPending}
          error={createEdition.error}
          onSubmit={(values) =>
            createEdition.mutate(
              { values, cover },
              {
                // A failed cover keeps the edition; its page shows the error.
                onSuccess: ({ edition, coverError }) =>
                  navigate(`/editions/${edition.id}`, {
                    state: coverError
                      ? { coverError: errorText(coverError) }
                      : null,
                  }),
              },
            )
          }
        >
          <CoverInput onSelect={setCover} />
        </EditionForm>
      </section>
    </>
  )
}
