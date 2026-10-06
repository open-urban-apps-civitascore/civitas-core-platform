import { describe, expect, it } from 'vitest'

import { UML_GEOMETRY_TYPES } from '@/components/uml-modeler/constants/umlTypes'

import { literalOutputPort, mappingRegistry } from './index'

describe('output ports', () => {
  it('label every output port with the subtype it produces', () => {
    for (const def of mappingRegistry.list) {
      for (const port of def.outputs) {
        expect(port.label, `output of ${def.type}`).toBe(port.dataType)
      }
    }
  })
})

describe('literalOutputPort', () => {
  it('labels a primitive port with its engine subtype', () => {
    expect(literalOutputPort('String')).toEqual({ id: 'out', label: 'str', type: 'scalar', dataType: 'str' })
    expect(literalOutputPort('Integer')).toEqual({ id: 'out', label: 'int', type: 'scalar', dataType: 'int' })
    expect(literalOutputPort('Boolean')).toEqual({ id: 'out', label: 'bool', type: 'scalar', dataType: 'bool' })
    expect(literalOutputPort('Date')).toEqual({ id: 'out', label: 'date', type: 'scalar', dataType: 'date' })
  })

  it('labels a Json port with the json engine subtype', () => {
    expect(literalOutputPort('Json')).toEqual({ id: 'out', label: 'json', type: 'scalar', dataType: 'json' })
  })

  it('labels a geometry port with the concrete geometry name', () => {
    expect(literalOutputPort('Point')).toEqual({ id: 'out', label: 'Point', type: 'geometry', dataType: 'Point' })
    expect(literalOutputPort('MultiPolygon')).toEqual({
      id: 'out',
      label: 'MultiPolygon',
      type: 'geometry',
      dataType: 'MultiPolygon',
    })
  })

  it('falls back to str for an unknown type name', () => {
    expect(literalOutputPort('Whatever')).toEqual({ id: 'out', label: 'str', type: 'scalar', dataType: 'str' })
  })

  it('labels the port with the configured value when one is set', () => {
    expect(literalOutputPort('Integer', '42')).toEqual({ id: 'out', label: '42', type: 'scalar', dataType: 'int' })
    expect(literalOutputPort('Integer', 42)).toEqual({ id: 'out', label: '42', type: 'scalar', dataType: 'int' })
    expect(literalOutputPort('String', '')).toEqual({ id: 'out', label: 'str', type: 'scalar', dataType: 'str' })
  })
})

describe('literal value placeholder', () => {
  it('shows a WKT example as the placeholder for every geometry type', () => {
    for (const type of UML_GEOMETRY_TYPES) {
      const placeholders = mappingRegistry.byType.const.placeholders?.({ type, value: '' })
      expect(placeholders?.value.startsWith(type.toUpperCase()), `${type} WKT`).toBe(true)
    }
  })

  it('shows a JSON example as the placeholder for the Json type', () => {
    const placeholders = mappingRegistry.byType.const.placeholders?.({ type: 'Json', value: '' })
    expect(placeholders?.value && JSON.parse(placeholders.value)).toEqual({ key: 'value' })
  })
})

describe('literal value control', () => {
  it('switches the value field to a textarea for the Json type', () => {
    const controls = mappingRegistry.byType.const.controls?.({ type: 'Json', value: '' })
    expect(controls).toEqual({ value: 'textarea' })
  })

  it('keeps the default text control for other types', () => {
    const controls = mappingRegistry.byType.const.controls?.({ type: 'String', value: '' })
    expect(controls).toBeUndefined()
  })
})
