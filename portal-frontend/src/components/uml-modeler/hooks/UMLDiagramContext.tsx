'use client'

import { createContext, useContext } from 'react'

import { createEmptyDiagram } from '../services/diagramService'
import type { UseUMLDiagramCoreReturn } from './use-uml-dagram-core'

// Context for sharing UML diagram state between components
const UMLDiagramContext = createContext<UseUMLDiagramCoreReturn | null>(null)

// Hook to consume the shared UML diagram state
export const useUMLDiagram = (): UseUMLDiagramCoreReturn => {
  const context = useContext(UMLDiagramContext)

  if (!context) {
    // Provide a fallback context for shared components when no provider exists
    // This allows components like ElementPalette to function without being inside a specific session
    const emptyDiagram = createEmptyDiagram()
    return {
      diagram: emptyDiagram,
      stats: {
        nodeCount: 0,
        edgeCount: 0,
        elementTypes: {},
        relationshipTypes: {},
      },
      isDirty: false,
      activeRelationshipType: 'association',
      dispatch: () => {},
      addNode: () => {},
      updateNode: () => {},
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
      loadDiagram: () => {},
      resetDiagram: () => {},
      markClean: () => {},
      markDirty: () => {},
      validateConnection: () => false,
    }
  }

  return context
}

// Export the context provider for direct use
export const UMLDiagramContextProvider = UMLDiagramContext.Provider
