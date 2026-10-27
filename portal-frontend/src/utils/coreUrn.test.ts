import { describe, expect, it } from 'vitest'

import { idOfUrn, latestUrnOf, logicalUrnOf, versionOfUrn } from './coreUrn'

const LOGICAL_URN = 'urn:core:platform:civitas:datastructure:common:3f2504e0-4f89-11d3-9a0c-0305e82c3301'
const PINNED_URN = `${LOGICAL_URN}:1.2.0`

describe('idOfUrn', () => {
  it('returns the UUID of a logical URN', () => {
    expect(idOfUrn(LOGICAL_URN)).toBe('3f2504e0-4f89-11d3-9a0c-0305e82c3301')
  })

  it('returns the UUID of a pinned URN', () => {
    expect(idOfUrn(PINNED_URN)).toBe('3f2504e0-4f89-11d3-9a0c-0305e82c3301')
  })

  // Without this test, a stored URN with dot segments could again redirect a request to another route.
  it.each([
    'urn:core:platform:civitas:mapping:common:../../3f2504e0-4f89-11d3-9a0c-0305e82c3301',
    'urn:core:platform:civitas:mapping:common:..',
    'urn:core:platform:civitas:mapping',
  ])('rejects a URN whose id segment is not a UUID: %s', urn => {
    expect(() => idOfUrn(urn)).toThrow()
  })
})

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

describe('latestUrnOf', () => {
  it('replaces the version segment of a pinned URN with latest', () => {
    expect(latestUrnOf(PINNED_URN)).toBe(`${LOGICAL_URN}:latest`)
  })

  it('appends latest to a URN without a version segment', () => {
    expect(latestUrnOf(LOGICAL_URN)).toBe(`${LOGICAL_URN}:latest`)
  })
})
