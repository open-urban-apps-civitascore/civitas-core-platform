/**
 * JSON Schema Import Service
 *
 * Rebuilds a UML diagram from a DataStructure model — the exact inverse of
 * jsonSchemaExportService, reading the same mapping table backwards. It exists
 * for versions that carry a model but no drawn diagram (bundle-imported
 * DataStructures store only the schema), so the editor can show the structure
 * instead of an empty canvas. It is strictly a gap-filler: callers invoke it
 * only when no `styles` diagram exists; a hand-drawn diagram is never touched.
 *
 * JSON Schema -> UML mapping (mirroring the export):
 * - Each `$defs` member becomes a class node; `enum` members become
 *   enumerations; `allOf: [{ $ref }, { ...own }]` becomes inheritance edges.
 * - Primitive `type`/`format` pairs map back to UML primitive types; GeoJSON
 *   `$ref`s map back to geometry types (a sibling `crs` is kept as gisInfo).
 * - A property `$ref` to a class member becomes a COMPOSITION edge (property
 *   name as role) — the schema cannot distinguish the three UML containment
 *   arrows, so the import commits to composition, matching the only structural
 *   relation the export understands. A `$ref` to an enumeration member becomes
 *   a typed attribute instead, which is how enums are conventionally drawn.
 * - `required` membership and array `minItems`/`maxItems` become multiplicity;
 *   `x-core-primaryKey` becomes the {id} attribute flag; the top-level `$ref`
 *   designates the `isRoot` element.
 * - Both `$defs` forms are handled: local `#/$defs/<Name>` references and the
 *   canonical split-ready form whose members carry Element CORE URNs as `$id`
 *   and cross-reference by URN.
 *
 * Documented detail loss (accepted trade-off): UML has no slot for
 * schema-only keywords (property-level descriptions, value bounds like
 * minimum/maximum, formats outside the primitive table), and every imported
 * classifier becomes a plain class. VIEWING loses nothing — the stored model
 * stays untouched — but saving a new version from a hydrated diagram
 * re-exports the schema from UML and drops what UML could not carry.
 *
 * Node positions are not part of a schema; the imported nodes are laid out on
 * the existing grid (`autoLayoutNodes`).
 */

import type { UMLDiagram, UMLEdge, UMLNode } from '../types/diagram'
import type {
  UMLAttribute,
  UMLClass,
  UMLElement,
  UMLEnumeration,
  UMLRelationship,
  UMLType,
  UMLTypeReference,
} from '../types/uml'
import { autoLayoutNodes } from './diagramService'
import { GEOJSON_REF_BASE } from './jsonSchemaExportService'

type JsonSchemaObject = Record<string, unknown>

const DEFS_REF_PREFIX = '#/$defs/'

/** Grid spacing for imported nodes — a little wider than the default so attribute-rich boxes clear each other. */
const IMPORT_GRID_SPACING = 280

/**
 * Reverse of the exporter's PRIMITIVE_TYPE_MAP, keyed by `type|format`. Kept
 * in lockstep with that table: a pair added there needs its reverse here.
 */
const PRIMITIVE_BY_TYPE_FORMAT = new Map<string, UMLAttribute['type']>([
  ['string|', 'String'],
  ['string|uuid', 'Uuid'],
  ['string|date', 'Date'],
  ['string|date-time', 'DateTime'],
  ['integer|', 'Integer'],
  ['number|', 'Number'],
  ['boolean|', 'Boolean'],
])

const isObject = (value: unknown): value is JsonSchemaObject =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

/** One `$defs` member, normalised: inheritance refs split off, the own object schema unwrapped. */
interface MemberShape {
  own: JsonSchemaObject
  parentRefs: string[]
  isEnum: boolean
}

/** Splits a member into its own schema and the `allOf` inheritance `$ref`s the export may have wrapped it in. */
const shapeOfMember = (member: JsonSchemaObject): MemberShape => {
  if (Array.isArray(member.allOf)) {
    const parentRefs: string[] = []
    let own: JsonSchemaObject = {}
    for (const entry of member.allOf) {
      if (!isObject(entry)) continue
      if (typeof entry.$ref === 'string' && Object.keys(entry).length === 1) parentRefs.push(entry.$ref)
      else own = entry
    }
    return { own, parentRefs, isEnum: false }
  }
  if (Array.isArray(member.enum)) return { own: member, parentRefs: [], isEnum: true }
  return { own: member, parentRefs: [], isEnum: false }
}

/** Lower/upper bounds of an array property, rendered back into a multiplicity string. */
const arrayMultiplicity = (schema: JsonSchemaObject): string => {
  const lower = typeof schema.minItems === 'number' ? schema.minItems : 0
  const upper = typeof schema.maxItems === 'number' ? schema.maxItems : null
  if (upper !== null) return `${lower}..${upper}`
  return lower === 0 ? '*' : `${lower}..*`
}

/** The geometry type name of a GeoJSON `$ref`, or null when the ref points elsewhere. */
const geometryTypeOfRef = (ref: string): string | null => {
  if (!ref.startsWith(`${GEOJSON_REF_BASE}/`) || !ref.endsWith('.json')) return null
  return ref.slice(GEOJSON_REF_BASE.length + 1, -'.json'.length)
}

/** Resolves local (`#/$defs/<Name>`) and canonical (Element-URN `$id`) references to a `$defs` key. */
const makeRefResolver = (defs: JsonSchemaObject): ((ref: string) => string | null) => {
  const keyByUrn = new Map<string, string>()
  for (const [key, member] of Object.entries(defs)) {
    if (isObject(member) && typeof member.$id === 'string') keyByUrn.set(member.$id, key)
  }
  return (ref: string) => {
    if (ref.startsWith(DEFS_REF_PREFIX)) {
      const key = ref.slice(DEFS_REF_PREFIX.length)
      return key in defs ? key : null
    }
    return keyByUrn.get(ref) ?? null
  }
}

interface ImportContext {
  elementIdByKey: Map<string, string>
  enumKeys: Set<string>
  nameByKey: Map<string, string>
  resolveRef: (ref: string) => string | null
  relationships: UMLRelationship[]
}

/** A property's schema with array wrapping removed, plus the derived multiplicity. */
const unwrapProperty = (
  propSchema: JsonSchemaObject,
  isRequired: boolean,
): { item: JsonSchemaObject; multiplicity: string | undefined } => {
  if (propSchema.type === 'array' && isObject(propSchema.items)) {
    return { item: propSchema.items, multiplicity: arrayMultiplicity(propSchema) }
  }
  // A mandatory single value is the UML default (1..1) and stays implicit, matching what the
  // export reads back; only the optional single needs an explicit bound.
  return { item: propSchema, multiplicity: isRequired ? undefined : '0..1' }
}

/** Converts one property's item schema into a UML attribute type (never a composition — the caller decides that). */
const attributeTypeOf = (item: JsonSchemaObject, context: ImportContext): { type: UMLType; crs?: string } => {
  if (typeof item.$ref === 'string') {
    const defKey = context.resolveRef(item.$ref)
    if (defKey) {
      const reference: UMLTypeReference = {
        id: context.elementIdByKey.get(defKey) as string,
        name: context.nameByKey.get(defKey) ?? defKey,
      }
      return { type: reference }
    }
    const geometry = geometryTypeOfRef(item.$ref)
    if (geometry) return { type: geometry as UMLType, crs: typeof item.crs === 'string' ? item.crs : undefined }
    // External reference: keep the href so the export can emit it verbatim again.
    const external: UMLTypeReference = {
      id: crypto.randomUUID(),
      name: item.$ref.split('/').pop() || item.$ref,
      isExternal: true,
      href: item.$ref,
    }
    return { type: external }
  }
  const key = `${typeof item.type === 'string' ? item.type : ''}|${typeof item.format === 'string' ? item.format : ''}`
  return { type: PRIMITIVE_BY_TYPE_FORMAT.get(key) ?? 'String' }
}

/** Builds one attribute from a property entry. */
const buildAttribute = (
  propName: string,
  item: JsonSchemaObject,
  multiplicity: string | undefined,
  context: ImportContext,
): UMLAttribute => {
  const { type, crs } = attributeTypeOf(item, context)
  const attribute: UMLAttribute = { id: crypto.randomUUID(), name: propName, type }
  if (multiplicity) attribute.multiplicity = multiplicity
  if (item['x-core-primaryKey'] === true) attribute.isId = true
  if (item.default !== undefined && item.default !== '') attribute.defaultValue = String(item.default)
  if (crs) attribute.meta = { gisInfo: { crs } }
  return attribute
}

/** True when the property should become a composition edge rather than a typed attribute. */
const isCompositionRef = (item: JsonSchemaObject, context: ImportContext): string | null => {
  if (typeof item.$ref !== 'string') return null
  const defKey = context.resolveRef(item.$ref)
  if (!defKey) return null
  // Enum references read as typed attributes, class references as containment.
  return context.enumKeys.has(defKey) ? null : defKey
}

/** Builds a class (or enumeration) element from one `$defs` member, collecting its edges into the context. */
const buildElement = (defKey: string, member: JsonSchemaObject, context: ImportContext): UMLElement => {
  const elementId = context.elementIdByKey.get(defKey) as string
  const { own, parentRefs, isEnum } = shapeOfMember(member)
  const name = context.nameByKey.get(defKey) ?? defKey

  if (isEnum) {
    const enumeration: UMLEnumeration = {
      id: elementId,
      name,
      type: 'enumeration',
      literals: (own.enum as unknown[]).map(literal => ({
        id: crypto.randomUUID(),
        name: String(literal),
        value: typeof literal === 'number' ? literal : String(literal),
      })),
    }
    return enumeration
  }

  for (const parentRef of parentRefs) {
    const parentKey = context.resolveRef(parentRef)
    if (!parentKey) continue
    context.relationships.push({
      id: crypto.randomUUID(),
      type: 'inheritance',
      // The parent sits at the edge target — the direction collectParentIds reads.
      source: elementId,
      target: context.elementIdByKey.get(parentKey) as string,
    })
  }

  const required = new Set(Array.isArray(own.required) ? (own.required as string[]) : [])
  const attributes: UMLAttribute[] = []

  for (const [propName, rawProp] of Object.entries(isObject(own.properties) ? own.properties : {})) {
    if (!isObject(rawProp)) continue
    const { item, multiplicity } = unwrapProperty(rawProp, required.has(propName))

    const partKey = isCompositionRef(item, context)
    if (partKey) {
      context.relationships.push({
        id: crypto.randomUUID(),
        type: 'composition',
        // The diamond (container) is drawn at the edge TARGET; role and multiplicity live on the
        // source (part) end — exactly where classifyStructuralEdge reads them back.
        source: context.elementIdByKey.get(partKey) as string,
        target: elementId,
        sourceRole: propName,
        sourceMultiplicity: multiplicity ?? '1',
      })
      continue
    }

    attributes.push(buildAttribute(propName, item, multiplicity, context))
  }

  const element: UMLClass = { id: elementId, name, type: 'class', attributes, operations: [] }
  if (typeof own.description === 'string') element.documentation = own.description
  return element
}

/**
 * Rebuilds a UML diagram from a DataStructure model. Returns null when the
 * model carries nothing drawable (no `$defs` members) or does not parse as the
 * export's output shape — callers fall back to the empty canvas, the exact
 * pre-hydration behaviour, rather than failing the page.
 */
export const importDiagramFromJsonSchema = (
  model: Record<string, unknown>,
  fallbackName?: string,
): UMLDiagram | null => {
  try {
    const defs = isObject(model.$defs) ? model.$defs : null
    if (!defs || Object.keys(defs).length === 0) return null

    const context: ImportContext = {
      elementIdByKey: new Map(),
      enumKeys: new Set(),
      nameByKey: new Map(),
      resolveRef: makeRefResolver(defs),
      relationships: [],
    }
    for (const [key, member] of Object.entries(defs)) {
      if (!isObject(member)) continue
      context.elementIdByKey.set(key, crypto.randomUUID())
      const { own, isEnum } = shapeOfMember(member)
      if (isEnum) context.enumKeys.add(key)
      context.nameByKey.set(key, typeof own.title === 'string' && own.title ? own.title : key)
    }

    const elements: UMLElement[] = []
    for (const [key, member] of Object.entries(defs)) {
      if (!isObject(member)) continue
      elements.push(buildElement(key, member, context))
    }

    // The top-level $ref designates the root; the flag restores the radio-semantic marker the
    // editor uses, so root resolution works without re-deriving containment.
    if (typeof model.$ref === 'string') {
      const rootKey = context.resolveRef(model.$ref)
      const rootId = rootKey ? context.elementIdByKey.get(rootKey) : undefined
      const root = elements.find(element => element.id === rootId)
      if (root) root.isRoot = true
    }

    const nodes: UMLNode[] = elements.map(element => ({
      id: element.id,
      type: element.type,
      position: { x: 0, y: 0 },
      data: { element, label: element.name },
    }))

    const edges: UMLEdge[] = context.relationships.map(relationship => ({
      id: crypto.randomUUID(),
      type: relationship.type,
      source: relationship.source,
      target: relationship.target,
      data: { relationship },
    }))

    return {
      id: crypto.randomUUID(),
      name: (typeof model.title === 'string' && model.title) || fallbackName || 'Untitled Diagram',
      nodes: autoLayoutNodes(nodes, IMPORT_GRID_SPACING),
      edges,
      lastModified: new Date(),
      isDirty: false,
    }
  } catch (error) {
    // A model this importer cannot read must degrade to the empty canvas, never break the page.
    console.warn('importDiagramFromJsonSchema: model not importable, showing empty canvas', error)
    return null
  }
}
