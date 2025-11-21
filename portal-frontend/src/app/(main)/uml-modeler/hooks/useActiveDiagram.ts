// useActiveDiagram.tsx
import type { Connection } from '@xyflow/react'
import { createContext, useContext } from 'react'

import type { getDiagramStats } from '../services/diagramService'
import type { DiagramAction, NodeCreationContext, UMLDiagram, UMLEdge, UMLNode } from '../types/diagram'

interface ActiveDiagramContextValue {
  // Current active diagram
  diagram: UMLDiagram | null
  stats: ReturnType<typeof getDiagramStats>
  isDirty: boolean
  activeRelationshipType: string

  // Selected elements
  selectedNode: UMLNode | undefined
  selectedEdge: UMLEdge | undefined

  // Actions
  dispatch: (action: DiagramAction) => void

  // Node operations
  addNode: (context: NodeCreationContext) => void
  updateNode: (nodeId: string, updates: any) => void
  deleteNodes: (nodeIds: string[]) => void
  selectNode: (nodeId: string, isMultiSelect?: boolean) => void

  // Edge operations
  addEdge: (connection: Connection) => void
  updateEdge: (edgeId: string, updates: any) => void
  deleteEdges: (edgeIds: string[]) => void
  selectEdge: (edgeId: string, isMultiSelect?: boolean) => void

  // Relationship type setting
  setActiveRelationshipType: (type: string) => void

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

export const useActiveDiagram = () => {
  const context = useContext(ActiveDiagramContext)
  if (!context) {
    throw new Error('useActiveDiagram must be used within ActiveDiagramProvider')
  }
  return context
}

export const ActiveDiagramProvider = ActiveDiagramContext.Provider
