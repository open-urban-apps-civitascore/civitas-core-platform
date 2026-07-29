/**
 * CORE URN construction for DataStructures.
 *
 * A DataStructure's URN is its stable identity: registry key, JSON Schema
 * `$ref` target and dependency-graph node. Every place that identifies a
 * DataStructure (e.g. UML editor model `$id`, mapping source/target) must build
 * it through here so the identities match.
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
const URN_ARTIFACT_TYPE = 'datastructure'

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
export const buildDataStructureUrn = (name: string, datastructureId: string, version: string): string => {
  const normalizedName = toPascalCaseName(name)
  if (!normalizedName)
    throw new Error(`buildDataStructureUrn: name yields no URN segment (got ${JSON.stringify(name)})`)
  if (!datastructureId) throw new Error('buildDataStructureUrn: datastructureId is required')
  if (!version) throw new Error('buildDataStructureUrn: version is required')

  const disambiguator = toDisambiguator(datastructureId)
  return `urn:core:${URN_SCOPE}:${URN_OWNER}:${URN_ARTIFACT_TYPE}:${URN_DOMAIN}:${normalizedName}:${disambiguator}:${version}`
}
