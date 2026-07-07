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
 * - composition/aggregation: the diamond (= the container) is drawn at the edge target, so the
 *   target contains the source.
 * - association: no diamond; keeps its drawn direction (source → target). The target becomes a
 *   property of the source and is therefore embedded; the source stays a possible document root.
 * - inheritance/realization: the parent sits at the edge target and is embedded into the subclass
 *   (the subclass is the concrete root).
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
 * Ids of every element that is embedded and therefore not a document root: the part side of any
 * structural edge (composition/aggregation part, association target — both end up as a property of
 * their container), and the parent (target) of an inheritance/realization edge. An embedded
 * element is reachable from a root, so surfacing it as its own root would duplicate it.
 */
export const collectContainedIds = (diagram: UMLDiagram): Set<string> => {
  const containedIds = new Set<string>()
  for (const edge of diagram.edges ?? []) {
    const rel = edge.data?.relationship
    if (!rel) continue
    if (INHERITANCE_RELATIONS.has(rel.type)) {
      containedIds.add(rel.target)
      continue
    }
    const containment = classifyStructuralEdge(rel)
    if (containment) containedIds.add(containment.partId)
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
 * The hierarchy roots: every non-embedded, non-enumeration element, in node order. A diagram may
 * legitimately hold several unconnected trees — each of their tops is a root, and the document
 * root (the data structure itself) references all of them, so nothing depends on which class was
 * inserted first. A fully embedded (e.g. cyclic) diagram has no derivable top, so every class
 * counts; an enumeration-only diagram falls back to the enumerations themselves.
 */
export const selectRootElements = (elements: UMLElement[], containedIds: Set<string>): UMLElement[] => {
  const nonEnum = elements.filter(e => e.type !== 'enumeration')
  const candidates = nonEnum.filter(e => !containedIds.has(e.id))
  if (candidates.length > 0) return candidates
  if (nonEnum.length > 0) return nonEnum
  return elements
}
