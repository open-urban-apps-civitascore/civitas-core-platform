/**
 * Structure Merge Service
 *
 * Puts a published structure into the diagram a modeller has open.
 *
 * Three rules decide the result, and all three come from what the export will later demand of the
 * diagram:
 *
 * - **A name that is taken is not overwritten.** The loaded class is added beside the one that is
 *   there, under a suffixed name. Two classes that merely normalize to the same name collide too,
 *   because that is the form a member's Element URN is derived from.
 * - **The existing root stays the root.** The loaded structure is hung under it, because the
 *   export refuses a diagram in which an element is not reachable from the root.
 * - **An empty diagram takes the loaded root as its own.**
 */

import { toPascalCaseName } from '@/utils/urn'

import type { ImportedStructureRef, UMLDiagram, UMLEdge, UMLNode } from '../types/diagram'
import type { UMLElement, UMLRelationship } from '../types/uml'
import { importFromJsonSchema } from './jsonSchemaImportService'
import { resolveRootElement } from './umlContainment'

type JsonSchemaObject = Record<string, unknown>

/** The gap between what is already drawn and what is loaded under it. */
const VERTICAL_GAP = 160

export interface StructureMergeResult {
  nodes: UMLNode[]
  edges: UMLEdge[]
  /** The element that must carry the root flag afterwards, or null when the flag does not move. */
  rootElementId: string | null
  /** Every class the merge had to rename, so the caller can say so. */
  renamed: { from: string; to: string }[]
  /** What the diagram was built from afterwards, this import included. */
  importedStructures: ImportedStructureRef[]
}

/**
 * Reads the document and merges it into the diagram. Answers the whole node and edge set, so the
 * caller applies one change rather than a sequence the editor would have to undo piecewise.
 */
export const mergeStructureIntoDiagram = (
  diagram: UMLDiagram,
  document: JsonSchemaObject,
  source: ImportedStructureRef,
): StructureMergeResult => {
  const imported = importFromJsonSchema(document, { origin: originBelow(diagram) })
  const renamed = renameTakenNames(diagram, imported.nodes, imported.edges)

  const existingRoot = resolveRootElement(diagram)
  const loadedRoot = imported.nodes.find(node => node.data.element.id === imported.rootElementId)

  const importedStructures = pin(diagram, source)
  if (existingRoot.kind === 'empty') {
    return { ...imported, renamed, importedStructures }
  }

  // The diagram already has classes, so its own root stays and the loaded one gives up its flag —
  // a second flag would make the diagram unexportable for a reason the modeller did not cause.
  if (loadedRoot) loadedRoot.data.element.isRoot = undefined

  const edges = [...imported.edges]
  if (existingRoot.kind !== 'invalid' && loadedRoot) {
    // The loaded structure becomes a part of what is there. Optional, because the modeller decides
    // whether a record carries it.
    edges.push(subordinate(loadedRoot.data.element, existingRoot.root))
  }
  // A diagram with classes but no resolvable root cannot be attached to. The loaded classes are
  // added as they are, and the export keeps reporting the defect that was there before.

  return { nodes: imported.nodes, edges, rootElementId: null, renamed, importedStructures }
}

/**
 * The diagram's pins with this one added. The same version loaded twice adds nothing: the classes
 * it brought are already there, under their own names.
 */
const pin = (diagram: UMLDiagram, source: ImportedStructureRef): ImportedStructureRef[] => {
  const pinned = diagram.importedStructures ?? []
  return pinned.some(candidate => candidate.urn === source.urn) ? pinned : [...pinned, source]
}

/** Where the loaded classes start: clear of everything already drawn. */
const originBelow = (diagram: UMLDiagram): { x: number; y: number } => {
  const nodes = diagram.nodes ?? []
  if (nodes.length === 0) return { x: 0, y: 0 }
  const left = Math.min(...nodes.map(node => node.position.x))
  const bottom = Math.max(...nodes.map(node => node.position.y))
  return { x: left, y: bottom + VERTICAL_GAP }
}

/**
 * Renames a loaded class whose name the diagram already carries. The suffix follows the one the
 * export uses for a `$defs` key, so a modeller sees the same form in both places.
 */
const renameTakenNames = (
  diagram: UMLDiagram,
  loaded: UMLNode[],
  loadedEdges: UMLEdge[],
): { from: string; to: string }[] => {
  const taken = new Set<string>()
  for (const node of diagram.nodes ?? []) {
    taken.add(toPascalCaseName(node.data.element.name))
  }

  const renamed: { from: string; to: string }[] = []
  for (const node of loaded) {
    const element = node.data.element
    const original = element.name
    let candidate = original
    let suffix = 1
    while (taken.has(toPascalCaseName(candidate))) {
      candidate = `${original}_${suffix++}`
    }
    taken.add(toPascalCaseName(candidate))
    if (candidate !== original) {
      element.name = candidate
      node.data.label = candidate
      renamed.push({ from: original, to: candidate })
      keepFieldNames(loadedEdges, element.id, original)
    }
  }
  return renamed
}

/**
 * Pins the field name a renamed part is held under. The import leaves the role out where the field
 * name follows from the class name, and the export derives it from the name again — after a rename
 * it would write another field than the document declared.
 */
const keepFieldNames = (edges: UMLEdge[], partId: string, originalName: string) => {
  for (const edge of edges) {
    const relationship = edge.data?.relationship
    if (relationship?.type === 'composition' && relationship.source === partId && !relationship.sourceRole) {
      relationship.sourceRole = originalName.charAt(0).toLowerCase() + originalName.slice(1)
    }
  }
}

/** A composition that makes the loaded structure a part of the element that is already the root. */
const subordinate = (part: UMLElement, container: UMLElement): UMLEdge => {
  const relationship: UMLRelationship = {
    id: crypto.randomUUID(),
    type: 'composition',
    source: part.id,
    target: container.id,
    sourceMultiplicity: '0..1',
  }
  return {
    id: relationship.id,
    source: part.id,
    target: container.id,
    type: 'composition',
    data: { relationship },
  }
}
