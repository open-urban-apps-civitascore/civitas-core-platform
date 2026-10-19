import { describe, expect, it } from 'vitest'

import type { UMLNode } from '../types/diagram'
import {
  getAttributeNameError,
  getElementNameError,
  getEnumValueNameError,
  getInvalidNodeIds,
  getNameSignature,
  getNextAttributeName,
  getNextElementName,
  getNextEnumValueName,
  hasInvalidNamesInDiagram,
} from './elementNameService'

const node = (id: string, name: string, attributeNames: string[] = []): UMLNode =>
  ({
    id,
    type: 'class',
    position: { x: 0, y: 0 },
    data: {
      element: {
        id,
        name,
        type: 'class',
        attributes: attributeNames.map((attributeName, index) => ({
          id: `${id}-${index}`,
          name: attributeName,
          type: 'String',
        })),
        operations: [],
      },
      label: name,
    },
  }) as unknown as UMLNode

const enumNode = (id: string, name: string, valueNames: string[] = []): UMLNode =>
  ({
    id,
    type: 'enumeration',
    position: { x: 0, y: 0 },
    data: {
      element: {
        id,
        name,
        type: 'enumeration',
        literals: valueNames.map((valueName, index) => ({ id: `${id}-${index}`, name: valueName })),
      },
      label: name,
    },
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

describe('hasInvalidNamesInDiagram', () => {
  it('is false for unique valid names', () => {
    expect(hasInvalidNamesInDiagram([node('a', 'Sensor'), node('b', 'Station')])).toBe(false)
  })

  it('is true for two classes with the same name', () => {
    expect(hasInvalidNamesInDiagram([node('a', 'Sensor'), node('b', 'Sensor')])).toBe(true)
  })

  it('is true for one class with invalid characters', () => {
    expect(hasInvalidNamesInDiagram([node('a', 'Foo Bar')])).toBe(true)
  })

  it('is true for an enumeration and a class with the same name', () => {
    expect(hasInvalidNamesInDiagram([node('a', 'Status'), enumNode('b', 'Status')])).toBe(true)
  })

  it('is true for an enumeration with invalid characters', () => {
    expect(hasInvalidNamesInDiagram([enumNode('a', 'Neue Aufzählung')])).toBe(true)
  })

  it('is true for two attributes of one class with the same name', () => {
    expect(hasInvalidNamesInDiagram([node('a', 'Sensor', ['value', 'value'])])).toBe(true)
  })

  it('is false for the same attribute name in two classes', () => {
    expect(hasInvalidNamesInDiagram([node('a', 'Sensor', ['value']), node('b', 'Station', ['value'])])).toBe(false)
  })

  it('is true for two values of one enumeration with the same name', () => {
    expect(hasInvalidNamesInDiagram([enumNode('a', 'Status', ['ACTIVE', 'ACTIVE'])])).toBe(true)
  })

  it('is false for the same value name in two enumerations', () => {
    expect(hasInvalidNamesInDiagram([enumNode('a', 'Status', ['ACTIVE']), enumNode('b', 'Mode', ['ACTIVE'])])).toBe(
      false,
    )
  })
})

describe('getInvalidNodeIds', () => {
  it('contains only the nodes with an invalid name', () => {
    const nodes = [node('a', 'Sensor'), node('b', 'Sensor'), node('c', 'Station')]

    expect(getInvalidNodeIds(nodes)).toEqual(new Set(['a', 'b']))
  })
})

describe('getNameSignature', () => {
  it('does not change when only a position changes', () => {
    const moved = { ...node('a', 'Sensor', ['value']), position: { x: 100, y: 50 } }

    expect(getNameSignature([moved])).toBe(getNameSignature([node('a', 'Sensor', ['value'])]))
  })

  it('changes when an attribute name changes', () => {
    expect(getNameSignature([node('a', 'Sensor', ['value'])])).not.toBe(
      getNameSignature([node('a', 'Sensor', ['level'])]),
    )
  })

  it('changes when an enumeration value name changes', () => {
    expect(getNameSignature([enumNode('a', 'Status', ['ACTIVE'])])).not.toBe(
      getNameSignature([enumNode('a', 'Status', ['INACTIVE'])]),
    )
  })
})

describe('getEnumValueNameError', () => {
  it('rejects a name another value already has', () => {
    expect(getEnumValueNameError('ACTIVE', ['ACTIVE'])).toBe('duplicateEnumValue')
  })

  it('accepts a unique name', () => {
    expect(getEnumValueNameError('ACTIVE', ['INACTIVE'])).toBeUndefined()
  })
})

describe('getNextEnumValueName', () => {
  it('takes the lowest free number', () => {
    expect(getNextEnumValueName(['WERT2', 'WERT3'])).toBe('WERT1')
  })

  it('does not repeat the name of the last value after a delete', () => {
    expect(getNextEnumValueName(['WERT1', 'WERT3'])).toBe('WERT2')
  })
})

describe('getAttributeNameError', () => {
  it('rejects a name another attribute already has', () => {
    expect(getAttributeNameError('value', ['value'])).toBe('duplicateAttribute')
  })

  it('accepts a unique name', () => {
    expect(getAttributeNameError('value', ['unit'])).toBeUndefined()
  })
})

describe('getNextAttributeName', () => {
  it('takes the lowest free number', () => {
    expect(getNextAttributeName(['neuesAttribut'])).toBe('neuesAttribut2')
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
