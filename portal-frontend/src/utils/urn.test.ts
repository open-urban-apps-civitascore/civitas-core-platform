import { describe, expect, it } from 'vitest'

import { buildDataStructureUrn, buildElementModelUrn, toLogicalUrn, toPascalCaseName } from './urn'

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
  const disambiguator = '2dmtus8w40'

  it('builds the full CORE URN with fixed namespace segments and a disambiguator segment', () => {
    expect(buildDataStructureUrn('Weather Model', id, '1.0.0')).toBe(
      `urn:core:platform:civitas:datastructure:common:WeatherModel:${disambiguator}:1.0.0`,
    )
  })

  it('derives a 10-char base36 disambiguator that keeps equal names apart', () => {
    const urn = buildDataStructureUrn('Bürgerdienste', id, '2.1.0')
    expect(urn).toBe(`urn:core:platform:civitas:datastructure:common:Buergerdienste:${disambiguator}:2.1.0`)
  })

  it('left-pads the disambiguator to a fixed length for low-valued ids', () => {
    const urn = buildDataStructureUrn('Weather Model', '00000000-0000-0000-0000-000000000001', '1.0.0')
    expect(urn).toBe('urn:core:platform:civitas:datastructure:common:WeatherModel:0000000001:1.0.0')
  })

  it('yields the same URN for the same DataStructure', () => {
    expect(buildDataStructureUrn('Weather Model', id, '1.0.0')).toBe(
      buildDataStructureUrn('Weather Model', id, '1.0.0'),
    )
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

describe('toLogicalUrn', () => {
  const logical = 'urn:core:platform:civitas:datasink:common:TrafficTable:2dmtus8w40'

  it('removes the version of a versioned URN', () => {
    expect(toLogicalUrn(`${logical}:1.0.0`)).toBe(logical)
  })

  it('returns a logical URN unchanged', () => {
    expect(toLogicalUrn(logical)).toBe(logical)
  })

  it('gives the same result for two versions of one artifact', () => {
    expect(toLogicalUrn(`${logical}:1.0.0`)).toBe(toLogicalUrn(`${logical}:2.3.0`))
  })
})

describe('buildElementModelUrn', () => {
  const id = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'
  const disambiguator = '2dmtus8w40'

  it('mirrors buildDataStructureUrn but with the element artifact-type segment', () => {
    expect(buildElementModelUrn('Weather Model', id, '1.0.0')).toBe(
      `urn:core:platform:civitas:element:common:WeatherModel:${disambiguator}:1.0.0`,
    )
  })

  it('shares name + disambiguator + version with the DataStructure URN, differing only in type', () => {
    const ds = buildDataStructureUrn('Weather Model', id, '2.1.0')
    const el = buildElementModelUrn('Weather Model', id, '2.1.0')
    expect(el).toBe(ds.replace(':datastructure:', ':element:'))
  })

  it('throws on the same invalid inputs as buildDataStructureUrn', () => {
    expect(() => buildElementModelUrn('', id, '1.0.0')).toThrow()
    expect(() => buildElementModelUrn('Weather Model', '', '1.0.0')).toThrow()
    expect(() => buildElementModelUrn('Weather Model', id, '')).toThrow()
  })
})
