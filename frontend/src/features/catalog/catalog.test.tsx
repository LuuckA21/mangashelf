import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../../App'
import type { EditionResponse, Role, SeriesDetail } from '../../api/types'
import { axe } from '../../test/axe'

const fetchMock = vi.fn()
type Reply = [status: number, body?: unknown]
let replies: Record<string, Reply | (() => Reply | Promise<Reply>)> = {}

const edition = (over: Partial<EditionResponse> = {}): EditionResponse => ({
  id: 'e1',
  seriesId: 's1',
  seriesTitle: 'Berserk',
  name: 'Deluxe',
  publisherId: 'p2',
  publisher: 'Panini',
  type: 'REGULAR',
  status: 'ONGOING',
  volumeCount: 5,
  hasCover: false,
  canEditCover: false,
  version: 3,
  collectionCount: 2,
  inMyCollection: false,
  ...over,
})
const detail = (editions = [edition()]): SeriesDetail => ({
  id: 's1',
  title: 'Berserk',
  editions,
})
const pageOf = (content: unknown[]) => ({
  content,
  page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 },
})
const berserk = { id: 's1', title: 'Berserk', editionCount: 2 }

function login(role: Role) {
  replies['GET /auth/me'] = [
    200,
    { id: '1', username: 'anna', role, mustChangePassword: false },
  ]
}

beforeEach(() => {
  replies = {}
  login('USER')
  fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
    const r = replies[`${init.method} ${url.replace('/api/v1', '')}`] ?? [
      404,
      { detail: `${init.method} ${url}` },
    ]
    const [status, body] = typeof r === 'function' ? await r() : r
    return new Response(body === undefined ? null : JSON.stringify(body), {
      status,
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  URL.createObjectURL = vi.fn(() => 'blob:preview')
  URL.revokeObjectURL = vi.fn()
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchMock.mockReset()
})

function renderApp(path: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const calls = (method: string, url: string) =>
  fetchMock.mock.calls.filter(
    (c) => c[1].method === method && c[0] === `/api/v1${url}`,
  )

describe('catalog page', () => {
  it('searchesSeries', async () => {
    replies['GET /series?page=0&size=20'] = [200, pageOf([berserk])]
    replies['GET /series?page=0&size=20&q=vin'] = [
      200,
      pageOf([{ id: 's2', title: 'Vinland', editionCount: 1 }]),
    ]
    renderApp('/catalog')
    expect(await screen.findByText('Berserk')).toBeInTheDocument()
    await userEvent.type(
      screen.getByLabelText('Cerca per titolo'),
      'vin{Enter}',
    )
    expect(await screen.findByText('Vinland')).toBeInTheDocument()
    expect(screen.queryByText('Berserk')).not.toBeInTheDocument()
  })

  it('showsEditionCount', async () => {
    replies['GET /series?page=0&size=20'] = [200, pageOf([berserk])]
    renderApp('/catalog')
    expect(await screen.findByText('2 edizioni')).toBeInTheDocument()
  })

  it('createsSeriesAndNavigatesToIt', async () => {
    replies['GET /series?page=0&size=20'] = [200, pageOf([])]
    replies['POST /series'] = [201, { ...berserk, editionCount: 0 }]
    replies['GET /series/s1'] = [200, detail([])]
    renderApp('/catalog')
    await userEvent.type(await screen.findByLabelText('Titolo'), ' Berserk ')
    await userEvent.click(screen.getByRole('button', { name: 'Crea serie' }))
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Berserk' }),
    ).toBeInTheDocument()
    expect(JSON.parse(calls('POST', '/series')[0][1].body)).toEqual({
      title: 'Berserk',
    })
  })

  it('duplicateTitleShows409Detail', async () => {
    replies['GET /series?page=0&size=20'] = [200, pageOf([berserk])]
    replies['POST /series'] = [409, { detail: 'Serie già presente' }]
    renderApp('/catalog')
    await userEvent.type(await screen.findByLabelText('Titolo'), 'berserk')
    await userEvent.click(screen.getByRole('button', { name: 'Crea serie' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Serie già presente')
    expect(alert).toHaveClass('error')
  })
})

describe('series page', () => {
  it('listsEditionsAndCollectionState', async () => {
    let added = false
    replies['GET /series/s1'] = () => [
      200,
      detail([
        edition({ inMyCollection: added }),
        edition({ id: 'e2', name: 'Maximum', inMyCollection: true }),
      ]),
    ]
    replies['PUT /collection/editions/e1'] = () => {
      added = true
      return [200, {}]
    }
    renderApp('/series/s1')
    expect(await screen.findByRole('link', { name: 'Deluxe' })).toHaveAttribute(
      'href',
      '/editions/e1',
    )
    expect(screen.getByText('Nella collezione')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /^Aggiungi/ })).toHaveLength(1)
    await userEvent.click(
      screen.getByRole('button', { name: /^Aggiungi alla mia collezione/ }),
    )
    expect(await screen.findAllByText('Nella collezione')).toHaveLength(2)
  })

  it('createsEditionThenUploadsCover', async () => {
    replies['GET /series/s1'] = [200, detail([])]
    replies['POST /series/s1/editions'] = [
      201,
      edition({ id: 'e9', name: 'Perfect' }),
    ]
    replies['PUT /editions/e9/cover'] = [204]
    replies['GET /editions/e9'] = [200, edition({ id: 'e9', name: 'Perfect' })]
    renderApp('/series/s1')
    await userEvent.type(
      await screen.findByLabelText('Nome edizione'),
      'Perfect',
    )
    await userEvent.selectOptions(screen.getByLabelText('Tipo'), 'VARIANT')
    await userEvent.clear(screen.getByLabelText('Numero di volumi'))
    await userEvent.type(screen.getByLabelText('Numero di volumi'), '12')
    const file = new File(['x'], 'c.png', { type: 'image/png' })
    await userEvent.upload(screen.getByLabelText('Copertina'), file)
    expect(screen.getByRole('img', { name: 'Anteprima' })).toHaveAttribute(
      'src',
      'blob:preview',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Crea edizione' }))
    expect(
      await screen.findByRole('heading', { level: 2, name: 'Perfect' }),
    ).toBeInTheDocument()
    expect(JSON.parse(calls('POST', '/series/s1/editions')[0][1].body)).toEqual(
      {
        name: 'Perfect',
        publisher: null,
        type: 'VARIANT',
        status: 'ONGOING',
        volumeCount: 12,
      },
    )
    const put = calls('PUT', '/editions/e9/cover')[0][1]
    expect(put.body).toBeInstanceOf(FormData)
    expect((put.body as FormData).get('file')).toBe(file)
  })

  it('coverFailureKeepsEditionAndShowsError', async () => {
    replies['GET /series/s1'] = [200, detail([])]
    replies['POST /series/s1/editions'] = [201, edition({ id: 'e9' })]
    replies['PUT /editions/e9/cover'] = [400, { detail: 'Immagine non valida' }]
    replies['GET /editions/e9'] = [200, edition({ id: 'e9' })]
    renderApp('/series/s1')
    await userEvent.type(await screen.findByLabelText('Nome edizione'), 'P')
    await userEvent.upload(
      screen.getByLabelText('Copertina'),
      new File(['x'], 'c.png', { type: 'image/png' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Crea edizione' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Immagine non valida')
    expect(alert).toHaveClass('error')
    expect(
      screen.getByRole('heading', { level: 2, name: 'Deluxe' }),
    ).toBeInTheDocument()
  })

  it('rejectsHugeCoverBeforeUpload', async () => {
    replies['GET /series/s1'] = [200, detail([])]
    renderApp('/series/s1')
    const big = new File(['x'], 'big.png', { type: 'image/png' })
    Object.defineProperty(big, 'size', { value: 5 * 1024 * 1024 + 1 })
    await userEvent.upload(await screen.findByLabelText('Copertina'), big)
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'L’immagine supera i 5 MB',
    )
    expect(screen.queryByRole('img', { name: 'Anteprima' })).toBeNull()
  })

  it('showsFieldErrorsAndFocusesFirst', async () => {
    replies['GET /series/s1'] = [200, detail([])]
    replies['POST /series/s1/editions'] = [
      400,
      {
        detail: 'Invalid',
        errors: [{ field: 'name', message: 'Nome troppo lungo' }],
      },
    ]
    renderApp('/series/s1')
    await userEvent.type(await screen.findByLabelText('Nome edizione'), 'P')
    await userEvent.click(screen.getByRole('button', { name: 'Crea edizione' }))
    const input = await screen.findByLabelText('Nome edizione')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription('Nome troppo lungo')
    expect(input).toHaveFocus()
  })

  it('adminSeesEditAndDelete', async () => {
    login('ADMIN')
    replies['GET /series/s1'] = [200, detail()]
    renderApp('/series/s1')
    await screen.findByRole('link', { name: 'Deluxe' })
    expect(screen.getByRole('button', { name: 'Rinomina serie' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Elimina serie' })).toBeVisible()
  })

  it('userDoesNot', async () => {
    replies['GET /series/s1'] = [200, detail()]
    renderApp('/series/s1')
    await screen.findByRole('link', { name: 'Deluxe' })
    expect(screen.queryByRole('button', { name: 'Rinomina serie' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Elimina serie' })).toBeNull()
  })

  it('deleteSeriesWithEditionsShows409', async () => {
    login('ADMIN')
    replies['GET /series/s1'] = [200, detail()]
    replies['DELETE /series/s1'] = [
      409,
      { detail: 'La serie ha delle edizioni' },
    ]
    renderApp('/series/s1')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Elimina serie' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'La serie ha delle edizioni',
    )
  })

  it('adminRenamesSeries', async () => {
    login('ADMIN')
    replies['GET /series/s1'] = [200, detail()]
    replies['PUT /series/s1'] = [200, berserk]
    renderApp('/series/s1')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Rinomina serie' }),
    )
    const input = screen.getByLabelText('Titolo')
    await userEvent.clear(input)
    await userEvent.type(input, 'Berserk 2')
    await userEvent.click(screen.getByRole('button', { name: 'Salva' }))
    expect(JSON.parse(calls('PUT', '/series/s1')[0][1].body)).toEqual({
      title: 'Berserk 2',
    })
  })

  it('hasNoAxeViolations', async () => {
    replies['GET /series/s1'] = [
      200,
      detail([
        edition(),
        edition({ id: 'e2', name: 'Maximum', inMyCollection: true }),
      ]),
    ]
    const { container } = renderApp('/series/s1')
    await screen.findByRole('link', { name: 'Deluxe' })
    expect((await axe(container)).violations).toEqual([])
  })
})

describe('edition page admin actions', () => {
  it('adminSeesEditAndDeleteOnEdition', async () => {
    login('ADMIN')
    replies['GET /editions/e1'] = [200, edition()]
    renderApp('/editions/e1')
    expect(
      await screen.findByRole('button', { name: 'Modifica edizione' }),
    ).toBeVisible()
    expect(
      screen.getByRole('button', { name: 'Elimina edizione' }),
    ).toBeVisible()
  })

  it('userDoesNotSeeAdminActions', async () => {
    replies['GET /editions/e1'] = [200, edition()]
    renderApp('/editions/e1')
    await screen.findByRole('heading', { level: 2, name: 'Deluxe' })
    expect(
      screen.queryByRole('button', { name: 'Modifica edizione' }),
    ).toBeNull()
    expect(
      screen.queryByRole('button', { name: 'Elimina edizione' }),
    ).toBeNull()
    expect(screen.queryByLabelText('Copertina')).toBeNull()
  })

  it('deleteEditionNamesCollectionCount', async () => {
    login('ADMIN')
    replies['GET /editions/e1'] = [200, edition({ collectionCount: 3 })]
    let deleted = false
    replies['GET /editions/e1'] = () =>
      deleted
        ? [404, { detail: 'Edizione non trovata' }]
        : [200, edition({ collectionCount: 3 })]
    replies['DELETE /editions/e1'] = () => {
      deleted = true
      return [204]
    }
    replies['GET /series/s1'] = [200, detail([])]
    renderApp('/editions/e1')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Elimina edizione' }),
    )
    expect(screen.getByRole('alertdialog')).toHaveTextContent(
      'è in 3 collezioni',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    // The edition page shows the same h1 while it leaves: wait for the series form.
    expect(await screen.findByLabelText('Nome edizione')).toBeInTheDocument()
    expect(calls('DELETE', '/editions/e1')).toHaveLength(1)
    expect(calls('GET', '/editions/e1')).toHaveLength(1)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('adminEditSendsVersion', async () => {
    login('ADMIN')
    replies['GET /editions/e1'] = [200, edition()]
    replies['PUT /editions/e1'] = [200, edition({ name: 'Nuova', version: 4 })]
    renderApp('/editions/e1')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Modifica edizione' }),
    )
    const name = screen.getByLabelText('Nome edizione')
    expect(name).toHaveValue('Deluxe')
    await userEvent.clear(name)
    await userEvent.type(name, 'Nuova')
    await userEvent.click(screen.getByRole('button', { name: 'Salva' }))
    await vi.waitFor(() => expect(calls('PUT', '/editions/e1')).toHaveLength(1))
    expect(JSON.parse(calls('PUT', '/editions/e1')[0][1].body)).toMatchObject({
      name: 'Nuova',
      version: 3,
      volumeCount: 5,
    })
  })

  it('staleEditShowsConflict', async () => {
    login('ADMIN')
    let current = edition()
    replies['GET /editions/e1'] = () => [200, current]
    replies['PUT /editions/e1'] = [
      409,
      { detail: 'Edizione modificata da altri' },
    ]
    renderApp('/editions/e1')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Modifica edizione' }),
    )
    current = edition({ name: 'Cambiata', version: 4 })
    await userEvent.click(screen.getByRole('button', { name: 'Salva' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Edizione modificata da altri Il modulo è stato ricaricato con i dati attuali.',
    )
    expect(await screen.findByLabelText('Nome edizione')).toHaveValue(
      'Cambiata',
    )
  })

  it('coverUploadAndRemoveWhenAllowed', async () => {
    replies['GET /editions/e1'] = [
      200,
      edition({ canEditCover: true, hasCover: true }),
    ]
    replies['PUT /editions/e1/cover'] = [204]
    replies['DELETE /editions/e1/cover'] = [204]
    renderApp('/editions/e1')
    const file = new File(['x'], 'c.jpg', { type: 'image/jpeg' })
    await userEvent.upload(await screen.findByLabelText('Copertina'), file)
    await vi.waitFor(() =>
      expect(calls('PUT', '/editions/e1/cover')).toHaveLength(1),
    )
    await userEvent.click(
      screen.getByRole('button', { name: 'Rimuovi copertina' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    await vi.waitFor(() =>
      expect(calls('DELETE', '/editions/e1/cover')).toHaveLength(1),
    )
  })
})

describe('review fixes', () => {
  const png = (name: string) => new File(['x'], name, { type: 'image/png' })

  it('rejectedCoverKeepsPreviousAcceptedFile', async () => {
    replies['GET /series/s1'] = [200, detail([])]
    replies['POST /series/s1/editions'] = [201, edition({ id: 'e9' })]
    replies['PUT /editions/e9/cover'] = [204]
    replies['GET /editions/e9'] = [200, edition({ id: 'e9' })]
    renderApp('/series/s1')
    await userEvent.type(await screen.findByLabelText('Nome edizione'), 'P')
    const a = png('a.png')
    await userEvent.upload(screen.getByLabelText('Copertina'), a)
    const big = png('big.png')
    Object.defineProperty(big, 'size', { value: 6 * 1024 * 1024 })
    await userEvent.upload(screen.getByLabelText('Copertina'), big)
    expect(screen.getByRole('alert')).toHaveTextContent('supera i 5 MB')
    expect(screen.getByRole('img', { name: 'Anteprima' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Crea edizione' }))
    await vi.waitFor(() =>
      expect(calls('PUT', '/editions/e9/cover')).toHaveLength(1),
    )
    const sent = calls('PUT', '/editions/e9/cover')[0][1].body as FormData
    expect(sent.get('file')).toBe(a)
  })

  it('failedCoverUploadDropsPreview', async () => {
    replies['GET /editions/e1'] = [200, edition({ canEditCover: true })]
    replies['PUT /editions/e1/cover'] = [400, { detail: 'Immagine non valida' }]
    renderApp('/editions/e1')
    await userEvent.upload(
      await screen.findByLabelText('Copertina'),
      png('a.png'),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Immagine non valida',
    )
    expect(screen.queryByRole('img', { name: 'Anteprima' })).toBeNull()
  })

  it('deleteSeriesSuccessNeverRefetchesIt', async () => {
    login('ADMIN')
    replies['GET /series/s1'] = [200, detail([])]
    replies['DELETE /series/s1'] = [204]
    replies['GET /series?page=0&size=20'] = [200, pageOf([])]
    renderApp('/series/s1')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Elimina serie' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Sì, elimina' }))
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Catalogo' }),
    ).toBeInTheDocument()
    expect(calls('GET', '/series/s1')).toHaveLength(1)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('seriesTitleFieldErrorIsLinkedAndFocused', async () => {
    replies['GET /series?page=0&size=20'] = [200, pageOf([])]
    replies['POST /series'] = [
      400,
      {
        detail: 'Invalid',
        errors: [{ field: 'title', message: 'Titolo vuoto' }],
      },
    ]
    renderApp('/catalog')
    await userEvent.type(await screen.findByLabelText('Titolo'), '   ')
    await userEvent.click(screen.getByRole('button', { name: 'Crea serie' }))
    const input = await screen.findByLabelText('Titolo')
    await vi.waitFor(() => expect(input).toHaveFocus())
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription('Titolo vuoto')
  })

  it('unmatchedFieldErrorIsShownInAlert', async () => {
    login('ADMIN')
    replies['GET /editions/e1'] = [200, edition()]
    replies['PUT /editions/e1'] = [
      400,
      {
        detail: 'Invalid',
        errors: [{ field: 'version', message: 'Versione mancante' }],
      },
    ]
    renderApp('/editions/e1')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Modifica edizione' }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Salva' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Versione mancante',
    )
  })

  it('nullPublisherHasNoLeadingSeparator', async () => {
    replies['GET /series/s1'] = [200, detail([edition({ publisher: null })])]
    renderApp('/series/s1')
    expect(
      await screen.findByText('Regolare · In corso · Volumi 5'),
    ).toBeInTheDocument()
  })
})
