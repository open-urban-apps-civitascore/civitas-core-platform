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
 * @param version - the SemVer version string (e.g. `1.0.0`); the form schemas
 *   enforce this shape (`VERSION_PATTERN`), the URN grammar rejects any other
 */
const buildCoreUrn = (artifactType: string, name: string, datastructureId: string, version: string): string => {
  const normalizedName = toPascalCaseName(name)
  if (!normalizedName) throw new Error(`buildCoreUrn: name yields no URN segment (got ${JSON.stringify(name)})`)
  if (!datastructureId) throw new Error('buildCoreUrn: datastructureId is required')
  if (!version) throw new Error('buildCoreUrn: version is required')

  const disambiguator = toDisambiguator(datastructureId)
  return `urn:core:${URN_SCOPE}:${URN_OWNER}:${artifactType}:${URN_DOMAIN}:${normalizedName}:${disambiguator}:${version}`
}

export const buildDataStructureUrn = (name: string, datastructureId: string, version: string): string =>
  buildCoreUrn(URN_TYPE_DATASTRUCTURE, name, datastructureId, version)

/**
 * Builds the versioned CORE URN for an Element that shares a DataStructure's disambiguator + version
 * and differs only in the artifact-type segment (`element` instead of `datastructure`) and, via
 * {@param name}, the name segment. Kept as the canonical Element-URN builder; a DataStructure's own
 * `$id` is now the DataStructure URN ({@link buildDataStructureUrn}), and its member Elements are
 * addressed with {@link elementModelUrnForMember}.
 */
export const buildElementModelUrn = (name: string, datastructureId: string, version: string): string =>
  buildCoreUrn(URN_TYPE_ELEMENT, name, datastructureId, version)

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
