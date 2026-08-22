import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { I18nProvider, useI18n } from './i18n'
import { formatCents, formatDate, formatPeriod, parseAmount } from './format'

function Probe() {
  const { language, setLanguage, t } = useI18n()
  return (
    <>
      <span>{t('settings.title')}</span>
      <button
        type="button"
        onClick={() => setLanguage(language === 'it' ? 'en' : 'it')}
      >
        switch
      </button>
    </>
  )
}

describe('internationalisation', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('mangashelf-language', 'it')
    document.documentElement.lang = 'it'
  })

  it('changes the interface language and persists it locally', async () => {
    render(
      <I18nProvider>
        <Probe />
      </I18nProvider>,
    )

    expect(screen.getByText('Impostazioni')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'switch' }))

    expect(await screen.findByText('Settings')).toBeInTheDocument()
    await waitFor(() => expect(document.documentElement.lang).toBe('en'))
    expect(localStorage.getItem('mangashelf-language')).toBe('en')
  })

  it('formats dates, periods and amounts with the selected locale', () => {
    expect(formatPeriod(2026, 7, 'it-CH')).toBe('luglio 2026')
    expect(formatPeriod(2026, 7, 'en-CH')).toBe('July 2026')
    expect(formatDate('2026-07-14', 'en-CH')).toBe('14 July 2026')
    expect(formatCents(123456, 'en-CH')).toBe("1'234.56")
    expect(parseAmount("1'234.56")).toBe(123456)
    expect(parseAmount('1’234,56')).toBe(123456)
  })
})
