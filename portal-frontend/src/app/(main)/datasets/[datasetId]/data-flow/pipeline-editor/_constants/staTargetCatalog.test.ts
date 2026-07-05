import { describe, expect, it } from 'vitest'

import { STA_ALLOWED_TARGET_PATHS, STA_GROUPS } from './staTargetCatalog'

/**
 * Pins the STA catalog mirror against the adapter's closed `StaTargetCatalog` (config-adapter-nifi,
 * `StaEnvelopeCompilerTest` pins the same list on the Java side). A path added or re-required on
 * one side only would let the editor accept a mapping the deploy then rejects — this test makes
 * that drift a visible failure instead of a silent divergence.
 */
describe('STA target catalog mirrors the adapter catalog', () => {
  it('pins the groups and their required paths', () => {
    expect(
      STA_GROUPS.map(group => ({
        arrayPath: group.arrayPath,
        requiredPaths: [...group.requiredPaths],
        optionalPaths: [...group.optionalPaths],
      })),
    ).toEqual([
      {
        arrayPath: '$.things',
        requiredPaths: ['$.things[].name', '$.things[].description', '$.things[].properties.reference'],
        optionalPaths: [],
      },
      {
        arrayPath: '$.observations',
        requiredPaths: [
          '$.observations[].result',
          '$.observations[].parameters.reference',
          '$.observations[].parameters.name',
        ],
        optionalPaths: ['$.observations[].phenomenonTime', '$.observations[].resultTime'],
      },
    ])
  })

  it('pins the full allowed-path whitelist', () => {
    expect([...STA_ALLOWED_TARGET_PATHS].sort()).toEqual(
      [
        '$.things[].name',
        '$.things[].description',
        '$.things[].properties.reference',
        '$.observations[].result',
        '$.observations[].phenomenonTime',
        '$.observations[].resultTime',
        '$.observations[].parameters.reference',
        '$.observations[].parameters.name',
      ].sort(),
    )
  })
})
