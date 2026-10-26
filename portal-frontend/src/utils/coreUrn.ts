/** `urn:core:<scope>:<owner>:<type>:<domain>:<uuid>` — a pin appends `:<version>`. */
const LOGICAL_SEGMENTS = 7
const ID_SEGMENT = 6

/** The UUID segment of a CORE URN, logical or pinned. */
export const idOfUrn = (urn: string): string => urn.split(':')[ID_SEGMENT]

/** The version segment of a pinned CORE URN, or nothing when it carries none. */
export const versionOfUrn = (urn: string): string => {
  const segments = urn.split(':')
  return segments.length > LOGICAL_SEGMENTS ? segments[LOGICAL_SEGMENTS] : ''
}

/** The CORE URN without its version segment. */
export const logicalUrnOf = (urn: string): string => urn.split(':').slice(0, LOGICAL_SEGMENTS).join(':')

/** The CORE URN that Model Forge resolves to the current version of the artifact. */
export const latestUrnOf = (urn: string): string => `${logicalUrnOf(urn)}:latest`

export const PINNED_DATASTRUCTURE_URN =
  /^urn:core:[^:]+:[^:]+:datastructure:[^:]+:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}:\d+\.\d+\.\d+$/
