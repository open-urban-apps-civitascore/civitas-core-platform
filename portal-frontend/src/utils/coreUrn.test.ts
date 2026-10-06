import { describe, expect, it } from 'vitest'

import { logicalUrnOf, versionOfUrn } from './coreUrn'

const LOGICAL_URN = 'urn:core:platform:civitas:datastructure:common:3f2504e0-4f89-11d3-9a0c-0305e82c3301'
const PINNED_URN = `${LOGICAL_URN}:1.2.0`

describe('versionOfUrn', () => {
  it('returns the version segment of a pinned URN', () => {
    expect(versionOfUrn(PINNED_URN)).toBe('1.2.0')
  })

  it('returns an empty string for a URN without a version segment', () => {
    expect(versionOfUrn(LOGICAL_URN)).toBe('')
  })
})

describe('logicalUrnOf', () => {
  it('strips the version segment from a pinned URN', () => {
    expect(logicalUrnOf(PINNED_URN)).toBe(LOGICAL_URN)
  })

  it('returns a URN without a version segment unchanged', () => {
    expect(logicalUrnOf(LOGICAL_URN)).toBe(LOGICAL_URN)
  })
})
