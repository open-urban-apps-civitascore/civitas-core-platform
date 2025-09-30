import { describe, expect, it } from 'vitest'

import { formatDate } from './formatDate'

describe('formatDate', () => {
  it('should format the date in DD.MM.YYYY for de locale', () => {
    const dateString = '2023-09-30'
    const locale = 'de'
    const expectedOutput = '30.09.2023'

    const result = formatDate(dateString, locale)

    expect(result).toBe(expectedOutput)
  })

  it('should format the date in DD/MM/YYYY for en locale', () => {
    const dateString = '2023-09-30'
    const locale = 'en'
    const expectedOutput = '30/09/2023'

    const result = formatDate(dateString, locale)

    expect(result).toBe(expectedOutput)
  })

  it('should format the date in DD/MM/YYYY for non-existing locales', () => {
    const dateString = '2023-09-30'
    const locale = 'fr'
    const expectedOutput = '30/09/2023'

    const result = formatDate(dateString, locale)

    expect(result).toBe(expectedOutput)
  })
})
