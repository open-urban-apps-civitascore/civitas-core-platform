/**
 * Shared UML containment semantics: the single source of containment direction and root-selection
 * rules consumed by the mapping-editor schema adapter and the JSON-Schema export service.
 *
 * Only relationship types in the supported release scope carry semantics here; every other type is
 * silently ignored (no containment, no parent), so a legacy model with out-of-scope edges still
 * maps — those edges simply contribute nothing. The scope itself is defined once by the palette
 * (constants/paletteItems.ts); widening it means adding the type there AND registering its
 * containment category in the set below.
 *
 * Direction rules:
 * - composition: the diamond (= the container) is drawn at the edge target, so the target contains
 *   the source.
 * - inheritance: the parent sits at the edge target and is embedded into the subclass (the subclass
 *   is the concrete root).
 */

import type { UMLDiagram } from '../types/diagram'
import type { UMLElement, UMLRelationship, UMLRelationshipType } from '../types/uml'

/** Structural part-of containments in scope. Its container sits at the edge target (diamond end). */
export const STRUCTURAL_RELATIONS: ReadonlySet<UMLRelationshipType> = new Set<UMLRelationshipType>(['composition'])

/** Structural relations whose container sits at the edge target (the diamond end). */
export const CONTAINER_AT_TARGET: ReadonlySet<UMLRelationshipType> = new Set<UMLRelationshipType>(['composition'])

/** Relationship types where the edge target is the parent embedded into the subclass. */
export const INHERITANCE_RELATIONS: ReadonlySet<UMLRelationshipType> = new Set<UMLRelationshipType>(['inheritance'])

/** A structural edge normalised into container/part orientation. */
export interface Containment {
  containerId: string
  partId: string
  role?: string
  multiplicity?: string
  isMany: boolean
}

/**
 * Lower/upper bounds of a multiplicity string; `upper === Infinity` is an unbounded (`*`) upper
 * bound. Unparseable input is treated as the UML default `1..1`.
 */
export const parseMultiplicity = (multiplicity?: string): { lower: number; upper: number } => {
  if (!multiplicity) return { lower: 1, upper: 1 }

  const trimmed = multiplicity.trim()
  if (trimmed === '') return { lower: 1, upper: 1 }
  if (trimmed === '*') return { lower: 0, upper: Infinity }

  const rangeMatch = trimmed.match(/^(\d+|\*)\.\.(\d+|\*)$/)
  if (rangeMatch) {
    const lower = rangeMatch[1] === '*' ? 0 : Number(rangeMatch[1])
    const upper = rangeMatch[2] === '*' ? Infinity : Number(rangeMatch[2])
    return { lower, upper }
  }

  const single = Number(trimmed)
  if (!Number.isNaN(single)) return { lower: single, upper: single }

  return { lower: 1, upper: 1 }
}

/** A multiplicity denotes a collection when its upper bound exceeds one. */
export const isManyMultiplicity = (multiplicity?: string): boolean => parseMultiplicity(multiplicity).upper > 1

/**
 * Normalises one relationship into container/part orientation. Returns `null` for edges that carry
 * no part-of containment — inheritance as well as any out-of-scope type (association, aggregation,
 * realization, dependency), which are ignored rather than mapped.
 */
export const classifyStructuralEdge = (rel: UMLRelationship): Containment | null => {
  if (!STRUCTURAL_RELATIONS.has(rel.type)) return null

  const isContainerAtTarget = CONTAINER_AT_TARGET.has(rel.type)
  const containerId = isContainerAtTarget ? rel.target : rel.source
  const partId = isContainerAtTarget ? rel.source : rel.target
  const role = isContainerAtTarget ? rel.sourceRole : rel.targetRole
  const multiplicity = isContainerAtTarget ? rel.sourceMultiplicity : rel.targetMultiplicity

  return { containerId, partId, role, multiplicity, isMany: isManyMultiplicity(multiplicity) }
}

/**
 * Ids of every element that is embedded and therefore cannot be the document root: the part side of
 * a composition, and the parent (target) of an inheritance edge.
 */
export const collectContainedIds = (diagram: UMLDiagram): Set<string> => {
  const containedIds = new Set<string>()
  for (const edge of diagram.edges ?? []) {
    const rel = edge.data?.relationship
    if (!rel) continue
    if (CONTAINER_AT_TARGET.has(rel.type)) {
      containedIds.add(rel.source)
    } else if (INHERITANCE_RELATIONS.has(rel.type)) {
      containedIds.add(rel.target)
    }
  }
  return containedIds
}

/** Inheritance parents of one element, in edge declaration order. */
export const collectParentIds = (diagram: UMLDiagram, elementId: string): string[] => {
  const parents: string[] = []
  for (const edge of diagram.edges ?? []) {
    const rel = edge.data?.relationship
    if (!rel || !INHERITANCE_RELATIONS.has(rel.type)) continue
    if (rel.source !== elementId) continue
    parents.push(rel.target)
  }
  return parents
}

/**
 * Picks the hierarchy root: a non-embedded, non-enumeration element. When several candidates remain
 * the choice is name-anchored case-insensitively (preferred name first, then the secondary name).
 * `pool[0]` is an order-dependent last resort that is only reached for a fully embedded (e.g. cyclic)
 * or multi-root diagram, where no single correct root exists.
 */
export const selectRootElement = (
  elements: UMLElement[],
  containedIds: Set<string>,
  preferredName: string | undefined,
  secondaryName?: string,
): UMLElement | undefined => {
  if (elements.length === 0) return undefined

  const nonEnum = elements.filter(e => e.type !== 'enumeration')
  const candidates = nonEnum.filter(e => !containedIds.has(e.id))
  let pool = elements
  if (candidates.length > 0) pool = candidates
  else if (nonEnum.length > 0) pool = nonEnum

  const preferred = (preferredName ?? '').toLowerCase()
  const secondary = (secondaryName ?? '').toLowerCase()
  return (
    pool.find(e => e.name.toLowerCase() === preferred) ??
    (secondary ? pool.find(e => e.name.toLowerCase() === secondary) : undefined) ??
    pool[0]
  )
}
