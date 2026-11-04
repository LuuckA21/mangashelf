import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { axe } from '../test/axe'
import ConfirmDelete from './ConfirmDelete'

describe('ConfirmDelete', () => {
  it('movesFocusToConfirmAndBackToTriggerOnCancel', async () => {
    const { container } = render(<ConfirmDelete what="x" onConfirm={vi.fn()} />)
    await userEvent.click(screen.getByRole('button', { name: 'Elimina' }))
    expect(
      screen.getByRole('alertdialog', { name: 'Eliminare x?' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sì, elimina' })).toHaveFocus()
    expect((await axe(container)).violations).toEqual([])
    await userEvent.click(screen.getByRole('button', { name: 'Annulla' }))
    expect(screen.getByRole('button', { name: 'Elimina' })).toHaveFocus()
  })
})
