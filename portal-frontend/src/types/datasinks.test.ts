import { describe, expect, it } from 'vitest'

import { DATASINK_TYPES } from './datasinks'

describe('DATASINK_TYPES', () => {
  it('offers exactly the FROST and PostGIS sink templates', () => {
    expect(DATASINK_TYPES).toEqual({ FROST: 'FROST', POSTGIS: 'POSTGIS' })
  })
})
