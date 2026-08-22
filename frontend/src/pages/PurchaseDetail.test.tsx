import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { catalog, purchases, type PurchaseList } from '../api/client'
import PurchaseDetail from './PurchaseDetail'

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return {
    ...actual,
    catalog: { ...actual.catalog, listManga: vi.fn(), listSeries: vi.fn() },
    purchases: Object.fromEntries(
      Object.keys(actual.purchases).map((name) => [name, vi.fn()]),
    ),
  }
})

vi.mock('../components/Layout', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

const getList = vi.mocked(purchases.get)
const setPurchased = vi.mocked(purchases.setPurchased)
const suggestions = vi.mocked(purchases.suggestions)
const listAll = vi.mocked(purchases.listAll)
const listManga = vi.mocked(catalog.listManga)

function purchaseList(purchasedAt: string | null = null): PurchaseList {
  return {
    id: 3,
    name: 'Agosto',
    periodYear: 2026,
    periodMonth: 8,
    paidAt: null,
    discountPercent: null,
    discountCents: null,
    items: [
      {
        id: 6,
        seriesId: 9,
        seriesName: 'Standard',
        publisher: 'Planet Manga',
        mangaTitle: 'Berserk',
        volumeNumber: 11,
        releaseDate: '2026-08-20',
        priceEurCents: 590,
        priceChfCents: 790,
        reserved: false,
        purchasedAt,
      },
    ],
    reservedCount: 0,
    purchasedCount: purchasedAt ? 1 : 0,
    totalEurCents: 590,
    subtotalChfCents: 790,
    discountAppliedCents: 0,
    totalChfCents: 790,
  }
}

function renderPurchase() {
  render(
    <MemoryRouter initialEntries={['/purchases/3']}>
      <Routes>
        <Route path="/purchases/:id" element={<PurchaseDetail />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('PurchaseDetail', () => {
  beforeEach(() => {
    getList.mockReset()
    setPurchased.mockReset()
    suggestions.mockReset().mockResolvedValue([])
    listAll.mockReset().mockResolvedValue([])
    listManga.mockReset().mockResolvedValue({
      content: [],
      size: 24,
      number: 0,
      totalElements: 0,
      totalPages: 0,
    })
  })

  it('marks a purchase through the accessible row control', async () => {
    getList.mockResolvedValue(purchaseList())
    setPurchased.mockResolvedValue(purchaseList('2026-08-20T12:00:00Z'))
    renderPurchase()

    const toggle = await screen.findByRole('button', {
      name: 'Berserk, volume 11: segna come acquistato',
    })
    expect(toggle).toHaveAttribute('aria-pressed', 'false')

    await userEvent.click(toggle)

    expect(setPurchased).toHaveBeenCalledWith(3, 6, true)
    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: 'Berserk, volume 11: segna come non acquistato',
        }),
      ).toHaveAttribute('aria-pressed', 'true'),
    )
  })
})
