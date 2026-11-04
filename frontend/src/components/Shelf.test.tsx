import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { axe } from '../test/axe'
import Shelf from './Shelf'

describe('Shelf', () => {
  it('togglesVolumeAndReportsNewSet', async () => {
    const onChange = vi.fn()
    render(<Shelf volumeCount={5} owned={[1]} onChange={onChange} />)
    await userEvent.click(
      screen.getByRole('button', { name: 'Volume 3, mancante' }),
    )
    expect(onChange).toHaveBeenCalledWith([1, 3])
    await userEvent.click(
      screen.getByRole('button', { name: 'Volume 1, posseduto' }),
    )
    expect(onChange).toHaveBeenLastCalledWith([])
  })

  it('markAllAndNone', async () => {
    const onChange = vi.fn()
    render(<Shelf volumeCount={3} owned={[2]} onChange={onChange} />)
    await userEvent.click(screen.getByRole('button', { name: 'Segna tutti' }))
    expect(onChange).toHaveBeenLastCalledWith([1, 2, 3])
    await userEvent.click(screen.getByRole('button', { name: 'Nessuno' }))
    expect(onChange).toHaveBeenLastCalledWith([])
  })

  it('ownedTilesArePressed', () => {
    render(<Shelf volumeCount={3} owned={[2]} onChange={vi.fn()} />)
    expect(
      screen.getByRole('button', { name: 'Volume 2, posseduto' }),
    ).toHaveAttribute('aria-pressed', 'true')
    expect(
      screen.getByRole('button', { name: 'Volume 1, mancante' }),
    ).toHaveAttribute('aria-pressed', 'false')
  })

  it('hasNoAxeViolations', async () => {
    const { container } = render(
      <Shelf volumeCount={12} owned={[1, 2, 5]} onChange={vi.fn()} busy />,
    )
    expect((await axe(container)).violations).toEqual([])
  })
})
