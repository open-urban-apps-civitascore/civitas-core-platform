'use client'

import type { Connection } from '@xyflow/react'
import { type ReactNode, useCallback, useMemo, useState } from 'react'

import { createUMLNode } from '../../constants/elementTemplates'
import { ActiveDiagramProvider } from '../../hooks/use-active-diagram'
import {
  createEmptyDiagram,
  diagramReducer,
  getDiagramStats,
  getSelectedEdges,
  getSelectedNodes,
  validateRelationshipConnection,
} from '../../services/diagramService'
import type { DiagramAction, NodeCreationContext, UMLDiagram, UMLEdge, UMLElementUpdate } from '../../types/diagram'
import type { UseMultiSessionReturn } from '../../types/session'
import type { UMLRelationship, UMLRelationshipType } from '../../types/uml'

interface ActiveDiagramProviderComponentProps {
  children: ReactNode
  sessionManager: UseMultiSessionReturn
}

const isSemanticDiagramChange = (action: DiagramAction): boolean => {
  if (action.type === 'MARK_CLEAN') return false
  if (action.type === 'NODE_CHANGES') {
    return action.payload.some(change => change.type !== 'select' && change.type !== 'dimensions')
  }
  if (action.type === 'EDGE_CHANGES') {
    return action.payload.some(change => change.type !== 'select')
  }
  return true
}

export const ActiveDiagramProviderComponent: React.FC<ActiveDiagramProviderComponentProps> = ({
  children,
  sessionManager,
}) => {
  const activeSession = sessionManager.getActiveSession()
  const [activeRelationshipType, setActiveRelationshipType] = useState<UMLRelationshipType | null>(null)

  const diagram = activeSession?.diagram || createEmptyDiagram()

  // Memoized values
  const selectedNode = useMemo(() => diagram.nodes.find(n => n.selected), [diagram.nodes])
  const selectedEdge = useMemo(() => diagram.edges.find(e => e.selected), [diagram.edges])
  const stats = useMemo(() => getDiagramStats(diagram), [diagram])

  // Helper to update diagram in session
  const updateDiagram = useCallback(
    (updater: (diagram: UMLDiagram) => UMLDiagram) => {
      if (!activeSession) return
      const updatedDiagram = updater(activeSession.diagram)
      sessionManager.updateSessionDiagram(activeSession.id, updatedDiagram)
      // Selection/viewport updates should not create model dirtiness.
      if (!activeSession.dirtyFields.has('model')) {
        sessionManager.markFieldClean(activeSession.id, 'model')
      }
    },
    [activeSession, sessionManager],
  )

  // Dispatch function that updates via session manager
  const dispatch = useCallback(
    (action: DiagramAction) => {
      if (!activeSession) return

      // Use the reducer to get the updated diagram
      const updatedDiagram = diagramReducer(activeSession.diagram, action)
      sessionManager.updateSessionDiagram(activeSession.id, updatedDiagram)

      if (isSemanticDiagramChange(action)) {
        sessionManager.markSessionDirty(activeSession.id, 'model')
      } else if (!activeSession.dirtyFields.has('model')) {
        sessionManager.markFieldClean(activeSession.id, 'model')
      }
    },
    [activeSession, sessionManager],
  )

  // Node operations
  const addNode = useCallback(
    (context: NodeCreationContext) => {
      const newNode = createUMLNode(context.elementType, context.position, context.name)
      dispatch({ type: 'ADD_NODE', payload: newNode })
    },
    [dispatch],
  )

  const updateNode = useCallback(
    (nodeId: string, updates: UMLElementUpdate) => {
      dispatch({ type: 'UPDATE_NODE', payload: { id: nodeId, updates } })
    },
    [dispatch],
  )

  const setRootNode = useCallback(
    (nodeId: string | null) => {
      dispatch({ type: 'SET_ROOT_NODE', payload: { id: nodeId } })
    },
    [dispatch],
  )

  const deleteNodes = useCallback(
    (nodeIds: string[]) => {
      dispatch({ type: 'DELETE_NODES', payload: nodeIds })
    },
    [dispatch],
  )

  const selectNode = useCallback(
    (nodeId: string, isMultiSelect = false) => {
      updateDiagram(diagram => ({
        ...diagram,
        nodes: diagram.nodes.map(node => ({
          ...node,
          selected: isMultiSelect ? (node.id === nodeId ? !node.selected : node.selected) : node.id === nodeId,
        })),
        edges: isMultiSelect ? diagram.edges : diagram.edges.map(edge => ({ ...edge, selected: false })),
      }))
    },
    [updateDiagram],
  )

  // Edge operations
  const addEdge = useCallback(
    (connection: Connection) => {
      if (!activeRelationshipType) return
      const edgeType = activeRelationshipType
      if (!validateRelationshipConnection(diagram, connection, edgeType)) return
      const newEdge: UMLEdge = {
        id: crypto.randomUUID(),
        type: edgeType,
        source: connection.source!,
        target: connection.target!,
        data: {
          relationship: {
            id: crypto.randomUUID(),
            type: edgeType,
            source: connection.source!,
            target: connection.target!,
          },
          label: '',
          isSelected: false,
          isDirty: true,
        },
      }

      dispatch({ type: 'SET_EDGES', payload: [...diagram.edges, newEdge] })
    },
    [diagram, activeRelationshipType, dispatch],
  )

  const updateEdge = useCallback(
    (edgeId: string, updates: Partial<UMLRelationship>) => {
      dispatch({ type: 'UPDATE_EDGE', payload: { id: edgeId, updates } })
    },
    [dispatch],
  )

  const deleteEdges = useCallback(
    (edgeIds: string[]) => {
      dispatch({ type: 'DELETE_EDGES', payload: edgeIds })
    },
    [dispatch],
  )

  const selectEdge = useCallback(
    (edgeId: string, isMultiSelect = false) => {
      updateDiagram(diagram => ({
        ...diagram,
        edges: diagram.edges.map(edge => ({
          ...edge,
          selected: isMultiSelect ? (edge.id === edgeId ? !edge.selected : edge.selected) : edge.id === edgeId,
        })),
        nodes: isMultiSelect ? diagram.nodes : diagram.nodes.map(node => ({ ...node, selected: false })),
      }))
    },
    [updateDiagram],
  )

  // Selection operations
  const clearSelection = useCallback(() => {
    updateDiagram(diagram => ({
      ...diagram,
      nodes: diagram.nodes.map(node => ({ ...node, selected: false })),
      edges: diagram.edges.map(edge => ({ ...edge, selected: false })),
    }))
  }, [updateDiagram])

  const selectAll = useCallback(() => {
    updateDiagram(diagram => ({
      ...diagram,
      nodes: diagram.nodes.map(node => ({ ...node, selected: true })),
      edges: diagram.edges.map(edge => ({ ...edge, selected: true })),
    }))
  }, [updateDiagram])

  const getSelectedNodesCallback = useCallback(() => getSelectedNodes(diagram), [diagram])
  const getSelectedEdgesCallback = useCallback(() => getSelectedEdges(diagram), [diagram])

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

  const validateConnectionCallback = useCallback(
    (connection: Connection) =>
      activeRelationshipType ? validateRelationshipConnection(diagram, connection, activeRelationshipType) : false,
    [diagram, activeRelationshipType],
  )

  const contextValue = useMemo(
    () => ({
      // State
      diagram,
      stats,
      isDirty: diagram.isDirty,
      activeRelationshipType,

      // Selected elements
      selectedNode,
      selectedEdge,

      // Actions
      dispatch,

      // Node operations
      addNode,
      updateNode,
      setRootNode,
      deleteNodes,
      selectNode,

      // Edge operations
      addEdge,
      updateEdge,
      deleteEdges,
      selectEdge,

      // Relationship type
      setActiveRelationshipType,

      // Selection operations
      clearSelection,
      selectAll,
      getSelectedNodes: getSelectedNodesCallback,
      getSelectedEdges: getSelectedEdgesCallback,
      deleteSelected,

      // Validation
      validateConnection: validateConnectionCallback,

      // Session info
      activeSessionId: activeSession?.id || null,
    }),
    [
      diagram,
      stats,
      activeRelationshipType,
      selectedNode,
      selectedEdge,
      dispatch,
      addNode,
      updateNode,
      setRootNode,
      deleteNodes,
      selectNode,
      addEdge,
      updateEdge,
      deleteEdges,
      selectEdge,
      setActiveRelationshipType,
      clearSelection,
      selectAll,
      getSelectedNodesCallback,
      getSelectedEdgesCallback,
      deleteSelected,
      validateConnectionCallback,
      activeSession?.id,
    ],
  )

  return <ActiveDiagramProvider value={contextValue}>{children}</ActiveDiagramProvider>
}
