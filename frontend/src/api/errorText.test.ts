import { describe, expect, it } from 'vitest'
import { t } from '../i18n/t'
import { ApiError, errorText } from './client'

describe('errorText', () => {
  const fields = new ApiError(400, 'Dati non validi', [
    { field: 'name', message: 'Obbligatorio' },
    { field: 'version', message: 'Obsoleta' },
  ])

  it('isEmptyWithoutAnError', () => {
    expect(errorText(undefined)).toBe('')
    expect(errorText(null, ['name'])).toBe('')
  })

  it('usesTheServerDetailOrAGenericText', () => {
    expect(errorText(new ApiError(409, 'In uso'))).toBe('In uso')
    expect(errorText(new TypeError('Failed to fetch'))).toBe(t('error.generic'))
  })

  it('leavesShownFieldsToTheFormAndKeepsTheOthers', () => {
    expect(errorText(fields, ['name'])).toBe('Obsoleta')
    expect(errorText(fields, ['name', 'version'])).toBe('')
    expect(errorText(new ApiError(409, 'In uso'), ['name'])).toBe('In uso')
  })

  it('withoutShownFieldsAlwaysUsesTheDetail', () => {
    expect(errorText(fields)).toBe('Dati non validi')
  })
})
