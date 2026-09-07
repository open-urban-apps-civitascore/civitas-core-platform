import { describe, expect, it } from 'vitest'

import messages from '@/messages/de.json'

import { NUMERIC_SUBTYPES } from '../_types'
import { mappingRegistry } from './index'

describe('numeric conversion inputs', () => {
  it('NUMERIC_SUBTYPES lists only numerically-parseable scalars', () => {
    expect([...NUMERIC_SUBTYPES]).toEqual(['str', 'int', 'number'])
    expect(NUMERIC_SUBTYPES).not.toContain('uuid')
    expect(NUMERIC_SUBTYPES).not.toContain('bool')
    expect(NUMERIC_SUBTYPES).not.toContain('date')
    expect(NUMERIC_SUBTYPES).not.toContain('datetime')
  })

  it('toInt and toNumber input ports accept only NUMERIC_SUBTYPES so uuid/bool/date are rejected', () => {
    expect(mappingRegistry.byType['toInt'].inputs[0].accepts).toEqual(NUMERIC_SUBTYPES)
    expect(mappingRegistry.byType['toFloat'].inputs[0].accepts).toEqual(NUMERIC_SUBTYPES)
  })

  it('toString stays permissive (any scalar → str): no accepts set', () => {
    expect(mappingRegistry.byType['toString'].inputs[0].accepts).toBeUndefined()
  })
})

describe('date conversion inputs', () => {
  it('toDate and toDateTime produce distinct output subtypes', () => {
    expect(mappingRegistry.byType['toDate'].outputs[0].dataType).toBe('date')
    expect(mappingRegistry.byType['toDateTime'].outputs[0].dataType).toBe('datetime')
  })

  it('toDate accepts str and date sources; toDateTime accepts str and datetime', () => {
    expect(mappingRegistry.byType['toDate'].inputs[0].accepts).toEqual(['str', 'date'])
    expect(mappingRegistry.byType['toDateTime'].inputs[0].accepts).toEqual(['str', 'datetime'])
  })

  it('format accepts both date and datetime sources', () => {
    expect(mappingRegistry.byType['format'].inputs[0].accepts).toEqual(['date', 'datetime'])
    expect(mappingRegistry.byType['format'].outputs[0].dataType).toBe('str')
  })
})

describe('displayed input types match the accepted subtypes', () => {
  const transformMessages = messages.pipelineEditor.mappingEditor.transforms
  const restricted = mappingRegistry.list.flatMap(def =>
    def.inputs.filter(port => port.accepts).map(port => ({ type: def.type, port })),
  )

  it('only toInt, toFloat, toDate, toDateTime and format restrict their input', () => {
    expect(restricted.map(entry => entry.type)).toEqual(['toInt', 'toFloat', 'toDate', 'toDateTime', 'format'])
  })

  it('each port label lists exactly the accepted subtypes', () => {
    for (const { type, port } of restricted) {
      expect(port.label, `label of ${type}`).toBe(port.accepts?.join(' / '))
    }
  })

  it('each palette description starts with the accepted subtypes', () => {
    for (const { type, port } of restricted) {
      const description = transformMessages[type as keyof typeof transformMessages].description
      expect(description, `description of ${type}`).toMatch(`${port.accepts?.join(' / ')} →`)
    }
  })

  it('toInt and toNumber show the same label, because both take the same subtypes', () => {
    expect(mappingRegistry.byType['toInt'].inputs[0].label).toBe('str / int / number')
    expect(mappingRegistry.byType['toFloat'].inputs[0].label).toBe('str / int / number')
  })

  it('toUuid has no accepts but one exact subtype, and shows that subtype', () => {
    expect(mappingRegistry.byType['toUuid'].inputs[0].label).toBe('str')
  })

  it('toString has neither, and shows "any scalar"', () => {
    expect(mappingRegistry.byType['toString'].inputs[0].label).toBe('any scalar')
  })
})

describe('uuid conversion input', () => {
  // Without toUuid a text source has no route to a uuid target at all: exact-subtype matching
  // rejects the edge and no other transform in the registry produces one.
  it('toUuid turns a str source into a uuid', () => {
    expect(mappingRegistry.byType['toUuid'].inputs[0].dataType).toBe('str')
    expect(mappingRegistry.byType['toUuid'].outputs[0].dataType).toBe('uuid')
  })
})
