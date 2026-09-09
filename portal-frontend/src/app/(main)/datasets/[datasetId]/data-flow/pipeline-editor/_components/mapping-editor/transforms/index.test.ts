import { describe, expect, it } from 'vitest'

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
})
