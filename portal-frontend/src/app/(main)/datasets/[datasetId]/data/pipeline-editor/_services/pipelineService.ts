/**
 * Pipeline Service
 *
 * Core service for pipeline diagram operations.
 * Provides factory functions and reducer for state management.
 *
 */

import type { Connection, EdgeChange, NodeChange } from '@xyflow/react'
import { applyEdgeChanges, applyNodeChanges } from '@xyflow/react'

import type { PipelineNodeData } from '../_types/nodes'
import type { Pipeline, PipelineAction, PipelineEdge, PipelineNode } from '../_types/pipeline'

// ============================================================================
// Factory Functions
// ============================================================================

/**
 * Creates an empty pipeline with no nodes or edges.
 *
 */
export const createEmptyPipeline = (name?: string): Pipeline => {
  const now = new Date()
  return {
    id: crypto.randomUUID(),
    name: name || 'Untitled Pipeline',
    description: '',
    nodes: [],
    edges: [],
    viewport: { x: 0, y: 0, zoom: 1 },
    createdAt: now,
    updatedAt: now,
    isDirty: false,
  }
}

// ============================================================================
// Pipeline Reducer
// ============================================================================

/**
 * Reducer for pipeline diagram state management.
 * Handles all node/edge operations following Redux pattern.
 *
 */
export const pipelineReducer = (state: Pipeline, action: PipelineAction): Pipeline => {
  switch (action.type) {
    case 'SET_NODES': {
      return {
        ...state,
        nodes: action.payload,
        updatedAt: new Date(),
        isDirty: true,
      }
    }

    case 'SET_EDGES': {
      return {
        ...state,
        edges: action.payload,
        updatedAt: new Date(),
        isDirty: true,
      }
    }

    case 'ADD_NODE': {
      return {
        ...state,
        nodes: [...state.nodes, action.payload],
        updatedAt: new Date(),
        isDirty: true,
      }
    }

    case 'ADD_EDGE': {
      return {
        ...state,
        edges: [...state.edges, action.payload],
        updatedAt: new Date(),
        isDirty: true,
      }
    }

    case 'UPDATE_NODE': {
      const { id, updates } = action.payload
      return {
        ...state,
        nodes: state.nodes.map(node =>
          node.id === id
            ? {
                ...node,
                data: { ...node.data, ...updates } as PipelineNodeData,
              }
            : node,
        ),
        updatedAt: new Date(),
        isDirty: true,
      }
    }

    case 'UPDATE_EDGE': {
      const { id, updates } = action.payload
      return {
        ...state,
        edges: state.edges.map(edge =>
          edge.id === id
            ? {
                ...edge,
                data: { ...edge.data, ...updates },
              }
            : edge,
        ),
        updatedAt: new Date(),
        isDirty: true,
      }
    }

    case 'DELETE_NODES': {
      const nodeIdsToDelete = new Set(action.payload)
      return {
        ...state,
        nodes: state.nodes.filter(node => !nodeIdsToDelete.has(node.id)),
        // Also delete edges connected to deleted nodes
        edges: state.edges.filter(edge => !nodeIdsToDelete.has(edge.source) && !nodeIdsToDelete.has(edge.target)),
        updatedAt: new Date(),
        isDirty: true,
      }
    }

    case 'DELETE_EDGES': {
      const edgeIdsToDelete = new Set(action.payload)
      return {
        ...state,
        edges: state.edges.filter(edge => !edgeIdsToDelete.has(edge.id)),
        updatedAt: new Date(),
        isDirty: true,
      }
    }

    case 'SET_VIEWPORT': {
      return {
        ...state,
        viewport: action.payload,
        updatedAt: new Date(),
      }
    }

    case 'MARK_CLEAN': {
      return {
        ...state,
        isDirty: false,
      }
    }

    case 'MARK_DIRTY': {
      return {
        ...state,
        isDirty: true,
        updatedAt: new Date(),
      }
    }

    case 'LOAD_PIPELINE': {
      return {
        ...action.payload,
        isDirty: false,
      }
    }

    case 'RESET_PIPELINE': {
      return createEmptyPipeline(state.name)
    }

    default:
      return state
  }
}

// ============================================================================
// Extended Actions (React Flow integration)
// ============================================================================

/**
 * Extended action types for React Flow integration.
 */
export type PipelineReducerAction =
  | PipelineAction
  | { type: 'NODE_CHANGES'; payload: NodeChange[] }
  | { type: 'EDGE_CHANGES'; payload: EdgeChange[] }

/**
 * Extended reducer that also handles React Flow native change events.
 *
 */
export const pipelineReducerWithReactFlow = (state: Pipeline, action: PipelineReducerAction): Pipeline => {
  switch (action.type) {
    case 'NODE_CHANGES': {
      return {
        ...state,
        nodes: applyNodeChanges(action.payload, state.nodes) as PipelineNode[],
        updatedAt: new Date(),
        isDirty: true,
      }
    }

    case 'EDGE_CHANGES': {
      return {
        ...state,
        edges: applyEdgeChanges(action.payload, state.edges) as PipelineEdge[],
        updatedAt: new Date(),
        isDirty: true,
      }
    }

    default:
      return pipelineReducer(state, action as PipelineAction)
  }
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Gets all selected nodes from the pipeline.
 *
 */
export const getSelectedNodes = (pipeline: Pipeline): PipelineNode[] => {
  return pipeline.nodes.filter(node => node.selected)
}

/**
 * Gets all selected edges from the pipeline.
 *
 */
export const getSelectedEdges = (pipeline: Pipeline): PipelineEdge[] => {
  return pipeline.edges.filter(edge => edge.selected)
}

/**
 * Validates if a connection between two nodes is allowed.
 * Basic validation - can be extended in Phase 5.
 *
 */
export const validateConnection = (pipeline: Pipeline, connection: Connection): boolean => {
  // Prevent self-connections
  if (connection.source === connection.target) {
    return false
  }

  // Prevent duplicate edges
  const existingEdge = pipeline.edges.find(
    edge => edge.source === connection.source && edge.target === connection.target,
  )
  if (existingEdge) {
    return false
  }

  return true
}

/**
 * Gets pipeline statistics.
 *
 */
export const getPipelineStats = (
  pipeline: Pipeline,
): {
  nodeCount: number
  edgeCount: number
  hasStartNode: boolean
  hasEndNode: boolean
} => {
  const hasStartNode = pipeline.nodes.some(node => node.type === 'start')
  const hasEndNode = pipeline.nodes.some(node => node.type === 'end')

  return {
    nodeCount: pipeline.nodes.length,
    edgeCount: pipeline.edges.length,
    hasStartNode,
    hasEndNode,
  }
}
