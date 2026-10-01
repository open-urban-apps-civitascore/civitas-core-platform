/** `urn:core:<scope>:<owner>:<type>:<domain>:<uuid>` — a pin appends `:<version>`. */
const LOGICAL_SEGMENTS = 7

/** The version segment of a pinned CORE URN, or nothing when it carries none. */
export const versionOfUrn = (urn: string): string => {
  const segments = urn.split(':')
  return segments.length > LOGICAL_SEGMENTS ? segments[LOGICAL_SEGMENTS] : ''
}

/** The CORE URN without its version segment. */
export const logicalUrnOf = (urn: string): string => urn.split(':').slice(0, LOGICAL_SEGMENTS).join(':')
