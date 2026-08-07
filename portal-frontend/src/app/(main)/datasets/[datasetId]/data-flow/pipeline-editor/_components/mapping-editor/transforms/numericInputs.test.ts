import { describe, expect, it } from 'vitest'

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
