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
import { hasAttributes } from '../types/uml'

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

/** Why a diagram yields no usable root; consumers render these as user-facing validation text. */
export type RootResolutionFailure =
  | { code: 'noRoot' }
  | { code: 'ambiguousRoot'; candidateNames: string[] }
  | { code: 'unreachable'; rootName: string; unreachableNames: string[] }

export type RootResolution =
  | { kind: 'empty' }
  | { kind: 'class'; root: UMLElement }
  | { kind: 'enum'; root: UMLElement }
  | { kind: 'invalid'; failure: RootResolutionFailure }

const elementsOf = (diagram: UMLDiagram): UMLElement[] =>
  (diagram.nodes ?? []).map(node => node.data?.element).filter((e): e is UMLElement => !!e)

const namesOf = (elements: UMLElement[]): string[] => elements.map(e => e.name || e.type)

/**
 * Every element id reachable from the root, following the directions in which the schema embeds:
 * structural edges container → part, inheritance/realization subclass → parent (the parent is
 * merged into the subclass), and attribute type references (an element used as an attribute's type
 * is embedded without any edge). A subclass of a reached class is deliberately NOT reached — its
 * own properties never appear in the record.
 */
export const collectReachableIds = (diagram: UMLDiagram, rootId: string): Set<string> => {
  const partsByContainer = new Map<string, string[]>()
  for (const edge of diagram.edges ?? []) {
    const rel = edge.data?.relationship
    if (!rel) continue
    if (INHERITANCE_RELATIONS.has(rel.type)) {
      partsByContainer.set(rel.source, [...(partsByContainer.get(rel.source) ?? []), rel.target])
      continue
    }
    const containment = classifyStructuralEdge(rel)
    if (containment) {
      partsByContainer.set(containment.containerId, [
        ...(partsByContainer.get(containment.containerId) ?? []),
        containment.partId,
      ])
    }
  }

  const elementById = new Map(elementsOf(diagram).map(e => [e.id, e]))
  const reachable = new Set<string>([rootId])
  const queue = [rootId]
  while (queue.length > 0) {
    const currentId = queue.shift() as string
    const next = [...(partsByContainer.get(currentId) ?? [])]
    const element = elementById.get(currentId)
    if (element && hasAttributes(element)) {
      for (const attr of element.attributes) {
        if (typeof attr.type !== 'string' && attr.type.id) next.push(attr.type.id)
      }
    }
    for (const id of next) {
      if (reachable.has(id)) continue
      reachable.add(id)
      queue.push(id)
    }
  }
  return reachable
}

/**
 * The single hierarchy root of a diagram: the explicitly flagged element (`isRoot`, set via the
 * editor's root checkbox) or, without a flag, strictly the one non-embedded, non-enumeration
 * element. From the root, every other element must be reachable. Anything else is `invalid` with
 * a typed failure instead of an order-dependent guess — several flags or several derivation
 * candidates (`ambiguousRoot`), a fully embedded/cyclic diagram (`noRoot`), or stranded elements
 * (`unreachable`). A diagram consisting of exactly one enumeration keeps its special enum-root
 * form.
 */
export const resolveRootElement = (diagram: UMLDiagram): RootResolution => {
  const elements = elementsOf(diagram)
  if (elements.length === 0) return { kind: 'empty' }

  const nonEnum = elements.filter(e => e.type !== 'enumeration')
  if (nonEnum.length === 0) {
    if (elements.length === 1) return { kind: 'enum', root: elements[0] }
    return { kind: 'invalid', failure: { code: 'ambiguousRoot', candidateNames: namesOf(elements) } }
  }

  const root = designatedRoot(nonEnum) ?? derivedRoot(nonEnum, diagram)
  if ('failure' in root) return { kind: 'invalid', failure: root.failure }

  const reachable = collectReachableIds(diagram, root.element.id)
  const unreachable = elements.filter(e => !reachable.has(e.id))
  if (unreachable.length > 0) {
    return {
      kind: 'invalid',
      failure: {
        code: 'unreachable',
        rootName: root.element.name || root.element.type,
        unreachableNames: namesOf(unreachable),
      },
    }
  }
  return { kind: 'class', root: root.element }
}

type RootPick = { element: UMLElement } | { failure: RootResolutionFailure }

/** The user-designated root, if any. Several flags (corrupt persisted state) are ambiguous. */
const designatedRoot = (nonEnum: UMLElement[]): RootPick | null => {
  const flagged = nonEnum.filter(e => e.isRoot === true)
  if (flagged.length === 0) return null
  if (flagged.length > 1) return { failure: { code: 'ambiguousRoot', candidateNames: namesOf(flagged) } }
  return { element: flagged[0] }
}

/** The containment-derived root: strictly the single non-embedded class. */
const derivedRoot = (nonEnum: UMLElement[], diagram: UMLDiagram): RootPick => {
  const containedIds = collectContainedIds(diagram)
  const candidates = nonEnum.filter(e => !containedIds.has(e.id))
  if (candidates.length === 0) return { failure: { code: 'noRoot' } }
  if (candidates.length > 1) {
    return { failure: { code: 'ambiguousRoot', candidateNames: namesOf(candidates) } }
  }
  return { element: candidates[0] }
}
