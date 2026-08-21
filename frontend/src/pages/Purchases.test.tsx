import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { purchases, type PurchaseStats } from '../api/client'
import Purchases from './Purchases'

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return {
    ...actual,
    purchases: { ...actual.purchases, listAll: vi.fn(), stats: vi.fn(), create: vi.fn() },
  }
})

vi.mock('../components/Layout', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

const listAll = vi.mocked(purchases.listAll)
const stats = vi.mocked(purchases.stats)

const purchaseStats: PurchaseStats = {
  years: [{
    year: 2026,
    listCount: 3,
    volumeCount: 12,
    fullChfCents: 9000,
    discountChfCents: 900,
    netChfCents: 8100,
    averageFullChfCents: 750,
    averageNetChfCents: 675,
  }],
  listCount: 3,
  volumeCount: 12,
  fullChfCents: 9000,
  discountChfCents: 900,
  netChfCents: 8100,
  averageFullChfCents: 750,
  averageNetChfCents: 675,
}

describe('Purchases', () => {
  beforeEach(() => {
    listAll.mockReset().mockResolvedValue([])
    stats.mockReset().mockResolvedValue(purchaseStats)
  })

  it('provides full-width creation and card-friendly statistics controls', async () => {
    render(<MemoryRouter><Purchases /></MemoryRouter>)

    expect(await screen.findByRole('button', { name: 'Nuova lista' }))
      .toHaveClass('new-purchase-list')
    const mobileStats = await screen.findByRole('region', { name: 'Statistiche per anno' })
    expect(mobileStats).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '2026' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Totale' })).toBeInTheDocument()
    expect(within(mobileStats).getAllByText('Risparmio')).toHaveLength(2)
  })
})
