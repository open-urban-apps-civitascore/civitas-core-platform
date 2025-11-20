'use client'

import { type Connection } from '@xyflow/react'
import { useCallback, useMemo, useReducer } from 'react'

import { createUMLNode } from '../constants/elementTemplates'
import {
  createEmptyDiagram,
  diagramReducer,
  getDiagramStats,
  getSelectedEdges,
  getSelectedNodes,
  validateConnection,
} from '../services/diagramService'
import type { DiagramAction, NodeCreationContext, UMLDiagram, UMLEdge, UMLNode } from '../types/diagram'
import type { UMLElement, UMLRelationship } from '../types/uml'

export interface UseUMLDiagramCoreReturn {
  // State
  diagram: UMLDiagram
  stats: ReturnType<typeof getDiagramStats>
  isDirty: boolean

  // Actions
  dispatch: (action: DiagramAction) => void

  // Node operations
  addNode: (context: NodeCreationContext) => void
  updateNode: (nodeId: string, updates: Partial<UMLElement>) => void
  deleteNodes: (nodeIds: string[]) => void
  selectNode: (nodeId: string, isMultiSelect?: boolean) => void

  // Edge operations
  addEdge: (connection: Connection) => void
  updateEdge: (edgeId: string, updates: Partial<UMLRelationship>) => void
  deleteEdges: (edgeIds: string[]) => void
  selectEdge: (edgeId: string, isMultiSelect?: boolean) => void

  // Selection operations
  clearSelection: () => void
  selectAll: () => void
  getSelectedNodes: () => UMLNode[]
  getSelectedEdges: () => UMLEdge[]
  deleteSelected: () => void

  // Diagram operations
  loadDiagram: (diagram: UMLDiagram) => void
  resetDiagram: () => void
  markClean: () => void
  markDirty: () => void

  // Validation
  validateConnection: (connection: Connection) => boolean
}

export const useUMLDiagramCore = (initialDiagram?: UMLDiagram): UseUMLDiagramCoreReturn => {
  const [diagram, dispatch] = useReducer(diagramReducer, initialDiagram ?? createEmptyDiagram())

  // Memoized stats calculation
  const stats = useMemo(() => getDiagramStats(diagram), [diagram])

  // Node operations
  const addNode = useCallback((context: NodeCreationContext) => {
    console.log('Adding node with context:', context)
    const newNode = createUMLNode(context.elementType, context.position, context.name)
    dispatch({ type: 'ADD_NODE', payload: newNode })
  }, [])

  const updateNode = useCallback((nodeId: string, updates: Partial<UMLElement>) => {
    dispatch({ type: 'UPDATE_NODE', payload: { id: nodeId, updates } })
  }, [])

  const deleteNodes = useCallback((nodeIds: string[]) => {
    dispatch({ type: 'DELETE_NODES', payload: nodeIds })
  }, [])

  const selectNode = useCallback(
    (nodeId: string, isMultiSelect = false) => {
      const updatedNodes = diagram.nodes.map(node => ({
        ...node,
        selected: isMultiSelect ? (node.id === nodeId ? !node.selected : node.selected) : node.id === nodeId,
      }))

      // Clear edge selection when selecting nodes
      const updatedEdges = diagram.edges.map(edge => ({
        ...edge,
        selected: false,
      }))

      dispatch({ type: 'SET_NODES', payload: updatedNodes })
      dispatch({ type: 'SET_EDGES', payload: updatedEdges })
    },
    [diagram.nodes, diagram.edges],
  )

  // Edge operations
  const addEdge = useCallback(
    (connection: Connection) => {
      if (validateConnection(diagram, connection)) {
        // Create basic relationship data
        const relationshipData = {
          id: crypto.randomUUID(),
          type: 'association' as const,
          source: connection.source!,
          target: connection.target!,
        }

        const edgeData = {
          relationship: relationshipData,
          label: '',
          isSelected: false,
          isDirty: true,
        }

        const newEdge: UMLEdge = {
          id: crypto.randomUUID(),
          type: 'association',
          source: connection.source!,
          target: connection.target!,
          data: edgeData,
        }

        dispatch({ type: 'SET_EDGES', payload: [...diagram.edges, newEdge] })
      }
    },
    [diagram],
  )

  const updateEdge = useCallback((edgeId: string, updates: Partial<UMLRelationship>) => {
    dispatch({ type: 'UPDATE_EDGE', payload: { id: edgeId, updates } })
  }, [])

  const deleteEdges = useCallback((edgeIds: string[]) => {
    dispatch({ type: 'DELETE_EDGES', payload: edgeIds })
  }, [])

  const selectEdge = useCallback(
    (edgeId: string, isMultiSelect = false) => {
      const updatedEdges = diagram.edges.map(edge => ({
        ...edge,
        selected: isMultiSelect ? (edge.id === edgeId ? !edge.selected : edge.selected) : edge.id === edgeId,
      }))

      // Clear node selection when selecting edges
      const updatedNodes = diagram.nodes.map(node => ({
        ...node,
        selected: false,
      }))

      dispatch({ type: 'SET_EDGES', payload: updatedEdges })
      dispatch({ type: 'SET_NODES', payload: updatedNodes })
    },
    [diagram.nodes, diagram.edges],
  )

  // Selection operations
  const clearSelection = useCallback(() => {
    const updatedNodes = diagram.nodes.map(node => ({ ...node, selected: false }))
    const updatedEdges = diagram.edges.map(edge => ({ ...edge, selected: false }))

    dispatch({ type: 'SET_NODES', payload: updatedNodes })
    dispatch({ type: 'SET_EDGES', payload: updatedEdges })
  }, [diagram.nodes, diagram.edges])

  const selectAll = useCallback(() => {
    const updatedNodes = diagram.nodes.map(node => ({ ...node, selected: true }))
    const updatedEdges = diagram.edges.map(edge => ({ ...edge, selected: true }))

    dispatch({ type: 'SET_NODES', payload: updatedNodes })
    dispatch({ type: 'SET_EDGES', payload: updatedEdges })
  }, [diagram.nodes, diagram.edges])

  const getSelectedNodesCallback = useCallback(() => {
    return getSelectedNodes(diagram)
  }, [diagram])

  const getSelectedEdgesCallback = useCallback(() => {
    return getSelectedEdges(diagram)
  }, [diagram])

  const deleteSelected = useCallback(() => {
    const selectedNodeIds = getSelectedNodes(diagram).map(node => node.id)
    const selectedEdgeIds = getSelectedEdges(diagram).map(edge => edge.id)

    if (selectedNodeIds.length > 0) {
      deleteNodes(selectedNodeIds)
    }
    if (selectedEdgeIds.length > 0) {
      deleteEdges(selectedEdgeIds)
    }
  }, [diagram, deleteNodes, deleteEdges])

  // Diagram operations
  const loadDiagram = useCallback((newDiagram: UMLDiagram) => {
    dispatch({ type: 'LOAD_DIAGRAM', payload: newDiagram })
  }, [])

  const resetDiagram = useCallback(() => {
    dispatch({ type: 'RESET_DIAGRAM' })
  }, [])

  const markClean = useCallback(() => {
    dispatch({ type: 'MARK_CLEAN' })
  }, [])

  const markDirty = useCallback(() => {
    dispatch({ type: 'MARK_DIRTY' })
  }, [])

  // Validation
  const validateConnectionCallback = useCallback(
    (connection: Connection) => {
      return validateConnection(diagram, connection)
    },
    [diagram],
  )

  return {
    // State
    diagram,
    stats,
    isDirty: diagram.isDirty,

    // Actions
    dispatch,

    // Node operations
    addNode,
    updateNode,
    deleteNodes,
    selectNode,

    // Edge operations
    addEdge,
    updateEdge,
    deleteEdges,
    selectEdge,

    // Selection operations
    clearSelection,
    selectAll,
    getSelectedNodes: getSelectedNodesCallback,
    getSelectedEdges: getSelectedEdgesCallback,
    deleteSelected,

    // Diagram operations
    loadDiagram,
    resetDiagram,
    markClean,
    markDirty,

    // Validation
    validateConnection: validateConnectionCallback,
  }
}
