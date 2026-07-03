import { describe, expect, it } from 'vitest'

import { buildDataStructureUrn, toPascalCaseName } from './urn'

describe('toPascalCaseName', () => {
  it('joins words into PascalCase', () => {
    expect(toPascalCaseName('weather model')).toBe('WeatherModel')
  })

  it('transliterates German umlauts and ß', () => {
    expect(toPascalCaseName('Bürgerdienste')).toBe('Buergerdienste')
    expect(toPascalCaseName('Straße')).toBe('Strasse')
  })

  it('splits on any run of non-alphanumeric characters', () => {
    expect(toPascalCaseName('Lärmkartierung – Hauptverkehrsstraßen (Tag/Nacht)')).toBe(
      'LaermkartierungHauptverkehrsstrassenTagNacht',
    )
  })

  it('preserves existing capitalization of the rest of each word', () => {
    expect(toPascalCaseName('GeoPoint sensor')).toBe('GeoPointSensor')
  })

  it('returns an empty string when nothing alphanumeric remains', () => {
    expect(toPascalCaseName('– / ()')).toBe('')
  })
})

describe('buildDataStructureUrn', () => {
  const id = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'

  it('builds the full CORE URN with fixed namespace segments', () => {
    expect(buildDataStructureUrn('Weather Model', id, '1.0.0')).toBe(
      `urn:core:platform:civitas:datastructure:common:WeatherModel-${id}:1.0.0`,
    )
  })

  it('appends the datastructure id to the name for uniqueness', () => {
    const urn = buildDataStructureUrn('Bürgerdienste', id, '2.1.0')
    expect(urn).toBe(`urn:core:platform:civitas:datastructure:common:Buergerdienste-${id}:2.1.0`)
  })

  it('throws when the name normalizes to an empty segment', () => {
    expect(() => buildDataStructureUrn('', id, '1.0.0')).toThrow()
    expect(() => buildDataStructureUrn('– / ()', id, '1.0.0')).toThrow()
  })

  it('throws when the datastructure id is missing', () => {
    expect(() => buildDataStructureUrn('Weather Model', '', '1.0.0')).toThrow()
  })

  it('throws when the version is missing', () => {
    expect(() => buildDataStructureUrn('Weather Model', id, '')).toThrow()
  })
})
