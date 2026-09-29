/**
 * CORE URN construction for DataStructures.
 *
 * A DataStructure's URN is its stable identity: registry key, JSON Schema
 * `$ref` target and dependency-graph node. Every place that identifies a
 * DataStructure (e.g. mapping source/target) must build it through here so the
 * identities match. The Element that backs a version's model uses
 * `buildElementModelUrn`, which shares the same name + disambiguator and differs
 * only in the artifact-type segment, so Model Forge's derived grouping URN still
 * matches `buildDataStructureUrn`.
 *
 * Format: `urn:core:<scope>:<owner>:<artifact-type>:<domain>:<name>:<disambiguator>:<version>`
 *
 * `scope`/`owner`/`domain` are fixed to the CORE namespace defaults; the tenant
 * is not available in the frontend, so `owner` cannot be derived per tenant.
 * The `name` segment carries the DataStructure name for readability; the
 * `disambiguator` segment keeps equal names from colliding — the display name is
 * not unique on its own.
 */

const URN_SCOPE = 'platform'
const URN_OWNER = 'civitas'
const URN_DOMAIN = 'common'
const URN_TYPE_DATASTRUCTURE = 'datastructure'
const URN_TYPE_ELEMENT = 'element'

const DISAMBIGUATOR_LENGTH = 10

/** The segments of a logical URN, from `urn` to the disambiguator. A version follows as the ninth. */
const LOGICAL_URN_SEGMENT_COUNT = 8

const UMLAUT_TRANSLITERATIONS: Record<string, string> = {
  ä: 'ae',
  ö: 'oe',
  ü: 'ue',
  Ä: 'Ae',
  Ö: 'Oe',
  Ü: 'Ue',
  ß: 'ss',
}

const transliterate = (value: string): string =>
  value.replace(/[äöüÄÖÜß]/g, char => UMLAUT_TRANSLITERATIONS[char] ?? char)

/**
 * Normalizes a display name to a PascalCase technical name for the URN `name`
 * segment: German umlauts are transliterated, then the value is split on any
 * run of non-alphanumeric characters and each word is capitalized.
 * `Lärmkartierung – Hauptverkehrsstraßen (Tag/Nacht)` →
 * `LaermkartierungHauptverkehrsstrassenTagNacht`.
 */
export const toPascalCaseName = (name: string): string =>
  transliterate(name)
    .split(/[^a-zA-Z0-9]+/)
    .filter(Boolean)
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join('')

/**
 * Derives the fixed-length base36 disambiguator from a DataStructure id.
 *
 * The id is a UUID; its hex digits are read as one 128-bit integer and encoded
 * in base36, then the least-significant {@link DISAMBIGUATOR_LENGTH} characters
 * are taken (left-padded when the value is short). Using the low-order digits
 * spreads the full UUID entropy across the token, whereas the high-order digits
 * of adjacent UUIDs often coincide. The mapping is deterministic, so the same
 * DataStructure always yields the same URN.
 */
const toDisambiguator = (datastructureId: string): string => {
  const hex = datastructureId.replace(/-/g, '')
  const base36 = BigInt(`0x${hex}`).toString(36)
  return base36.slice(-DISAMBIGUATOR_LENGTH).padStart(DISAMBIGUATOR_LENGTH, '0')
}

/**
 * Builds the versioned CORE URN identifying a DataStructure version.
 *
 * Throws when any segment would be empty: the URN is a stable identity and a
 * `$ref` target, so a malformed one must surface at the call site rather than
 * be persisted. Callers must not invoke this before their inputs are available.
 *
 * @param name - the DataStructure display name (not unique on its own); must
 *   contain at least one alphanumeric character after normalization
 * @param datastructureId - the DataStructure id (UUID); source of the
 *   disambiguator that keeps equal names apart
 * @param version - the SemVer version string a pin needs (e.g. `1.0.0`), taken from the version
 *   the registry assigned. Omitted for a logical (version-free) identity
 */
const buildCoreUrn = (artifactType: string, name: string, datastructureId: string, version?: string): string => {
  const normalizedName = toPascalCaseName(name)
  if (!normalizedName) throw new Error(`buildCoreUrn: name yields no URN segment (got ${JSON.stringify(name)})`)
  if (!datastructureId) throw new Error('buildCoreUrn: datastructureId is required')

  const disambiguator = toDisambiguator(datastructureId)
  const identity = `urn:core:${URN_SCOPE}:${URN_OWNER}:${artifactType}:${URN_DOMAIN}:${normalizedName}:${disambiguator}`
  return version ? `${identity}:${version}` : identity
}

/**
 * Builds the versioned CORE URN of one DataStructure version — a pin. Use it wherever a reference
 * must keep resolving to the same content, such as a mapping's source and target. The version must
 * be the one the registry assigned (read it off the version resource); it is never authored.
 *
 * Throws when a segment would be empty, including the version: a pin without one would silently
 * become a floating reference.
 */
export const buildDataStructureUrn = (name: string, datastructureId: string, version: string): string => {
  if (!version) throw new Error('buildDataStructureUrn: version is required for a pin')
  return buildCoreUrn(URN_TYPE_DATASTRUCTURE, name, datastructureId, version)
}

/**
 * Builds the logical (version-free) CORE URN of a DataStructure — its identity across all versions.
 * This is the form a saved model carries as its `$id`: the registry is the version authority and
 * assigns the version on store, so a client that authored one would only be stating a guess.
 */
export const buildDataStructureLogicalUrn = (name: string, datastructureId: string): string =>
  buildCoreUrn(URN_TYPE_DATASTRUCTURE, name, datastructureId)

/**
 * Returns the logical (version-free) form of a CORE URN. A URN without a version comes back
 * unchanged. Use it to compare two references to the same artifact: a stored reference keeps the
 * version it had when it was written, and the artifact can have a newer version now.
 */
export const toLogicalUrn = (urn: string): string => {
  const segments = urn.split(':')
  const logicalSegments = segments.slice(0, LOGICAL_URN_SEGMENT_COUNT)
  return logicalSegments.join(':')
}

/**
 * Builds the versioned CORE URN for an Element that shares a DataStructure's disambiguator + version
 * and differs only in the artifact-type segment (`element` instead of `datastructure`) and, via
 * {@param name}, the name segment. Kept as the canonical Element-URN builder; a DataStructure's own
 * `$id` is now the DataStructure URN ({@link buildDataStructureUrn}), and its member Elements are
 * addressed with {@link elementModelUrnForMember}.
 */
export const buildElementModelUrn = (name: string, datastructureId: string, version: string): string => {
  if (!version) throw new Error('buildElementModelUrn: version is required for a pin')
  return buildCoreUrn(URN_TYPE_ELEMENT, name, datastructureId, version)
}

/**
 * Builds the Element URN for a member of a DataStructure from the DataStructure's own (versioned) URN.
 * The member Element shares the DataStructure's disambiguator + version; only the artifact-type segment
 * (→ `element`) and the name segment (→ the member's PascalCase name) change. Used to stamp `$id` on
 * each `$defs` member of an exported DataStructure so Model Forge splits them into separate Element
 * artifacts under stable, name-based URNs (rather than minting UUID-based ones).
 *
 * @param dataStructureUrn - the DataStructure's CORE URN (`urn:core:…:datastructure:…`)
 * @param memberName - the member's display name; PascalCased into the URN name segment
 */
export const elementModelUrnForMember = (dataStructureUrn: string, memberName: string): string => {
  // urn:core:<scope>:<owner>:<type>:<domain>:<name>:<disambiguator>[:<version>]
  const parts = dataStructureUrn.split(':')
  if (parts.length < 8 || parts[0] !== 'urn' || parts[1] !== 'core')
    throw new Error(`elementModelUrnForMember: not a CORE URN: ${JSON.stringify(dataStructureUrn)}`)
  const normalizedName = toPascalCaseName(memberName)
  if (!normalizedName)
    throw new Error(`elementModelUrnForMember: member name yields no URN segment (got ${JSON.stringify(memberName)})`)
  parts[4] = URN_TYPE_ELEMENT
  parts[6] = normalizedName
  return parts.join(':')
}
