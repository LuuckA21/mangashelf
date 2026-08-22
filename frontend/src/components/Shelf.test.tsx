import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import Shelf from './Shelf'

describe('Shelf', () => {
  it('keeps volume zero visible and exposes every toggle by name', async () => {
    const onToggle = vi.fn().mockResolvedValue(undefined)
    render(<Shelf upTo={2} ownedNumbers={[0, 2]} onToggle={onToggle} />)

    const zero = screen.getByRole('button', {
      name: 'Volume 0 posseduto: rimuovi dalla collezione',
    })
    expect(zero).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(zero)

    expect(onToggle).toHaveBeenCalledWith(0, true)
    expect(
      screen.getByRole('button', {
        name: 'Volume 1 mancante: aggiungi alla collezione',
      }),
    ).toHaveAttribute('aria-pressed', 'false')
  })
})
