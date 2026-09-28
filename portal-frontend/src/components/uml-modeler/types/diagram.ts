import type { Connection, Edge, EdgeChange, Node, NodeChange } from '@xyflow/react'

import type { UMLElement, UMLElementType, UMLRelationship, UMLRelationshipType } from './uml'

// Omit that distributes over the UMLElement union — a plain Omit would collapse it to the common
// keys and lose variant-specific fields (literals, attributes, …).
type DistributiveOmit<T, K extends PropertyKey> = T extends unknown ? Omit<T, K> : never

/**
 * Per-node element update. `isRoot` is excluded so the radio invariant (at most one flag
 * diagram-wide) has exactly one writer by construction: the SET_ROOT_NODE action.
 */
export type UMLElementUpdate = Partial<DistributiveOmit<UMLElement, 'isRoot'>>

// UML Node data structure
export interface UMLNodeData extends Record<string, unknown> {
  element: UMLElement
  label: string
  isSelected?: boolean
  isDirty?: boolean // Has unsaved changes
}

// UML Edge data structure
export interface UMLEdgeData extends Record<string, unknown> {
  relationship: UMLRelationship
  label?: string
  isSelected?: boolean
  isDirty?: boolean
}

// Extended ReactFlow Node with UML data
export interface UMLNode extends Node<UMLNodeData, UMLElementType> {
  type: UMLElementType
  data: UMLNodeData
}

// Extended ReactFlow Edge with UML relationship data
export interface UMLEdge extends Edge<UMLEdgeData, UMLRelationshipType> {
  type: UMLRelationshipType
  data: UMLEdgeData
}

/**
 * A structure of the platform that was loaded into this diagram, pinned at the version it had.
 * A later version of that structure leaves the diagram untouched, which is why the version is kept
 * rather than the identity alone.
 */
export interface ImportedStructureRef {
  urn: string
  name: string
}

// Diagram state
export interface UMLDiagram {
  id: string
  name: string
  description?: string
  nodes: UMLNode[]
  edges: UMLEdge[]
  viewport?: {
    x: number
    y: number
    zoom: number
  }
  lastModified: Date
  isDirty: boolean
  /** The published structures loaded into this diagram, in the order they were loaded. */
  importedStructures?: ImportedStructureRef[]
}

// Diagram operations
export type DiagramAction =
  | { type: 'SET_NODES'; payload: UMLNode[] }
  | { type: 'SET_EDGES'; payload: UMLEdge[] }
  | { type: 'NODE_CHANGES'; payload: NodeChange[] }
  | { type: 'EDGE_CHANGES'; payload: EdgeChange[] }
  | { type: 'ADD_EDGE'; payload: Connection }
  | { type: 'ADD_NODE'; payload: UMLNode }
  | { type: 'UPDATE_NODE'; payload: { id: string; updates: UMLElementUpdate } }
  // Root designation is radio-semantic (at most one flag diagram-wide), so it is one atomic
  // action instead of per-node updates; `id: null` clears the designation entirely.
  | { type: 'SET_ROOT_NODE'; payload: { id: string | null } }
  | { type: 'UPDATE_EDGE'; payload: { id: string; updates: Partial<UMLRelationship> } }
  | { type: 'DELETE_NODES'; payload: string[] }
  | { type: 'DELETE_EDGES'; payload: string[] }
  | { type: 'SET_VIEWPORT'; payload: { x: number; y: number; zoom: number } }
  // One published structure loaded into the diagram. Atomic on purpose: the provider recomputes
  // every dispatch from the diagram of the current render, so a sequence of actions inside one
  // handler would have the last one overwrite the ones before it.
  | {
      type: 'MERGE_STRUCTURE'
      payload: { nodes: UMLNode[]; edges: UMLEdge[]; importedStructures: ImportedStructureRef[] }
    }
  | { type: 'MARK_CLEAN' }
  | { type: 'MARK_DIRTY' }
  | { type: 'LOAD_DIAGRAM'; payload: UMLDiagram }
  | { type: 'RESET_DIAGRAM' }

// Node creation context
export interface NodeCreationContext {
  elementType: UMLElementType
  position: { x: number; y: number }
  name?: string
}

// Edge creation context
export interface EdgeCreationContext {
  relationshipType: UMLRelationshipType
  source: string
  target: string
  sourceHandle?: string
  targetHandle?: string
}

// Selection state
export interface SelectionState {
  selectedNodes: string[]
  selectedEdges: string[]
  lastSelected?: {
    type: 'node' | 'edge'
    id: string
  }
}

// Canvas interaction modes
export type InteractionMode = 'select' | 'create-association' | 'create-inheritance' | 'create-realization'

// Diagram validation result
export interface ValidationResult {
  isValid: boolean
  errors: ValidationError[]
  warnings: ValidationWarning[]
}

export interface ValidationError {
  id: string
  type: 'node' | 'edge'
  elementId: string
  message: string
  severity: 'error'
}

export interface ValidationWarning {
  id: string
  type: 'node' | 'edge'
  elementId: string
  message: string
  severity: 'warning'
}

// Layout options
export interface LayoutOptions {
  algorithm: 'hierarchical' | 'force' | 'circular' | 'grid'
  direction?: 'TB' | 'BT' | 'LR' | 'RL' // Top-Bottom, Bottom-Top, Left-Right, Right-Left
  spacing: {
    node: number
    rank: number
  }
}

// Export/Import formats
export type DiagramFormat = 'json' | 'xmi' | 'png' | 'svg' | 'pdf'

export interface ExportOptions {
  format: DiagramFormat
  includeMetadata?: boolean
  imageOptions?: {
    width?: number
    height?: number
    backgroundColor?: string
    quality?: number
  }
}

// Undo/Redo state
export interface HistoryState {
  past: UMLDiagram[]
  present: UMLDiagram
  future: UMLDiagram[]
  maxHistorySize: number
}

export type HistoryAction =
  | { type: 'UNDO' }
  | { type: 'REDO' }
  | { type: 'PUSH_STATE'; payload: UMLDiagram }
  | { type: 'CLEAR_HISTORY' }
  | { type: 'SET_MAX_HISTORY_SIZE'; payload: number }
