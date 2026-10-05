import { describe, expect, it } from 'vitest'

import type { UMLNode } from '../types/diagram'
import { getElementNameError, getNextElementName, hasInvalidNames } from './elementNameService'

const node = (id: string, name: string): UMLNode =>
  ({
    id,
    type: 'class',
    position: { x: 0, y: 0 },
    data: { element: { id, name, type: 'class', attributes: [], operations: [] }, label: name },
  }) as unknown as UMLNode

const enumNode = (id: string, name: string): UMLNode =>
  ({
    id,
    type: 'enumeration',
    position: { x: 0, y: 0 },
    data: { element: { id, name, type: 'enumeration', literals: [] }, label: name },
  }) as unknown as UMLNode

describe('getElementNameError', () => {
  it.each(['Sensor', 'sensor_value', '_Sensor', 'Sensor2'])('accepts %s', name => {
    expect(getElementNameError(name, [])).toBeUndefined()
  })

  it.each(['2Sensor', 'Foo Bar', 'Foo-Bar', 'Straße', ' Sensor'])('rejects the characters of %s', name => {
    expect(getElementNameError(name, [])).toBe('invalidCharacters')
  })

  it('requires a name', () => {
    expect(getElementNameError('', [])).toBe('required')
  })

  it('rejects a name another class already has', () => {
    expect(getElementNameError('Sensor', ['Sensor'])).toBe('duplicateElement')
  })

  it('accepts a name that differs from another only in case', () => {
    expect(getElementNameError('sensor', ['Sensor'])).toBeUndefined()
  })
})

describe('hasInvalidNames', () => {
  it('is false for unique valid names', () => {
    expect(hasInvalidNames([node('a', 'Sensor'), node('b', 'Station')])).toBe(false)
  })

  it('is true for two classes with the same name', () => {
    expect(hasInvalidNames([node('a', 'Sensor'), node('b', 'Sensor')])).toBe(true)
  })

  it('is true for one class with invalid characters', () => {
    expect(hasInvalidNames([node('a', 'Foo Bar')])).toBe(true)
  })

  it('is true for an enumeration and a class with the same name', () => {
    expect(hasInvalidNames([node('a', 'Status'), enumNode('b', 'Status')])).toBe(true)
  })

  it('is true for an enumeration with invalid characters', () => {
    expect(hasInvalidNames([enumNode('a', 'Neue Aufzählung')])).toBe(true)
  })
})

describe('getNextElementName', () => {
  it('keeps the default name when it is free', () => {
    expect(getNextElementName('class', ['Sensor'])).toBe('NeueKlasse')
  })

  it('takes the lowest free number', () => {
    expect(getNextElementName('class', ['NeueKlasse', 'NeueKlasse3'])).toBe('NeueKlasse2')
  })
})
