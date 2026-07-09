'use client'

import type { Connection } from '@xyflow/react'
import { createContext, useContext } from 'react'

import { createEmptyDiagram, getDiagramStats } from '../services/diagramService'
import type {
  DiagramAction,
  NodeCreationContext,
  UMLDiagram,
  UMLEdge,
  UMLElementUpdate,
  UMLNode,
} from '../types/diagram'
import type { UMLRelationship, UMLRelationshipType } from '../types/uml'

interface ActiveDiagramContextValue {
  // Current active diagram
  diagram: UMLDiagram
  stats: ReturnType<typeof getDiagramStats>
  isDirty: boolean
  // null = no relationship tool selected (neutral). Connections cannot be drawn until a tool is picked.
  activeRelationshipType: UMLRelationshipType | null

  // Selected elements
  selectedNode: UMLNode | undefined
  selectedEdge: UMLEdge | undefined

  // Actions
  dispatch: (action: DiagramAction) => void

  // Node operations
  addNode: (context: NodeCreationContext) => void
  updateNode: (nodeId: string, updates: UMLElementUpdate) => void
  /** Designates the node as the diagram's root (clearing any other flag); `null` clears it. */
  setRootNode: (nodeId: string | null) => void
  deleteNodes: (nodeIds: string[]) => void
  selectNode: (nodeId: string, isMultiSelect?: boolean) => void

  // Edge operations
  addEdge: (connection: Connection) => void
  updateEdge: (edgeId: string, updates: Partial<UMLRelationship>) => void
  deleteEdges: (edgeIds: string[]) => void
  selectEdge: (edgeId: string, isMultiSelect?: boolean) => void

  // Relationship type setting
  setActiveRelationshipType: (type: UMLRelationshipType | null) => void

  // Selection operations
  clearSelection: () => void
  selectAll: () => void
  getSelectedNodes: () => UMLNode[]
  getSelectedEdges: () => UMLEdge[]
  deleteSelected: () => void

  // Validation
  validateConnection: (connection: Connection) => boolean

  // Session info
  activeSessionId: string | null
}

const ActiveDiagramContext = createContext<ActiveDiagramContextValue | null>(null)

export const useActiveDiagram = (): ActiveDiagramContextValue => {
  const context = useContext(ActiveDiagramContext)

  if (!context) {
    // Provide a fallback context for shared components when no provider exists
    // This allows components like ElementPalette to function without being inside a specific session
    const emptyDiagram = createEmptyDiagram()
    return {
      diagram: emptyDiagram,
      stats: getDiagramStats(emptyDiagram),
      isDirty: false,
      activeRelationshipType: null,
      selectedNode: undefined,
      selectedEdge: undefined,
      dispatch: () => {},
      addNode: () => {},
      updateNode: () => {},
      setRootNode: () => {},
      deleteNodes: () => {},
      selectNode: () => {},
      addEdge: () => {},
      updateEdge: () => {},
      deleteEdges: () => {},
      selectEdge: () => {},
      setActiveRelationshipType: () => {},
      clearSelection: () => {},
      selectAll: () => {},
      getSelectedNodes: () => [],
      getSelectedEdges: () => [],
      deleteSelected: () => {},
      validateConnection: () => false,
      activeSessionId: null,
    }
  }

  return context
}

export const ActiveDiagramProvider = ActiveDiagramContext.Provider
