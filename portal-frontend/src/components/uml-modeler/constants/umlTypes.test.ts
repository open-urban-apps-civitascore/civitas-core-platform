import { describe, expect, it } from 'vitest'

import { cardinalityForDisplay, cardinalityForStorage, PROPERTY_CARDINALITY_VALUES } from './umlTypes'

describe('cardinalityForDisplay', () => {
  it('shows an unset multiplicity as "1"', () => {
    expect(cardinalityForDisplay(undefined)).toBe('1')
    expect(cardinalityForDisplay('')).toBe('1')
  })

  it('passes a set multiplicity through unchanged', () => {
    for (const value of PROPERTY_CARDINALITY_VALUES) {
      expect(cardinalityForDisplay(value)).toBe(value)
    }
  })

  it('passes a non-dropdown multiplicity through unchanged so it stays visible', () => {
    expect(cardinalityForDisplay('*')).toBe('*')
    expect(cardinalityForDisplay('1..5')).toBe('1..5')
  })
})

describe('cardinalityForStorage', () => {
  it('clears the field when "1" is selected (unset already means exactly-one)', () => {
    expect(cardinalityForStorage('1')).toBeUndefined()
  })

  it('persists every other dropdown value verbatim', () => {
    expect(cardinalityForStorage('0..1')).toBe('0..1')
    expect(cardinalityForStorage('0..*')).toBe('0..*')
    expect(cardinalityForStorage('1..*')).toBe('1..*')
  })
})

describe('cardinality round-trip', () => {
  // An unset attribute that the user opens and re-selects as "1" must stay unset,
  // keeping pre-existing models byte-identical.
  it('treats unset and an explicit "1" selection as identical storage', () => {
    expect(cardinalityForStorage(cardinalityForDisplay(undefined))).toBeUndefined()
  })

  it('round-trips every dropdown value through display and back to storage', () => {
    for (const value of PROPERTY_CARDINALITY_VALUES) {
      const stored = cardinalityForStorage(cardinalityForDisplay(value))
      // "1" normalises to undefined; every other value is preserved.
      expect(stored).toBe(value === '1' ? undefined : value)
    }
  })
})
