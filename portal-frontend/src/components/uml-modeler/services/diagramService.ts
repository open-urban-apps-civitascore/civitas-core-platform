import {
  addEdge,
  applyEdgeChanges,
  applyNodeChanges,
  type Connection,
  type EdgeChange,
  type NodeChange,
} from '@xyflow/react'

import type { DiagramAction, UMLDiagram, UMLEdge, UMLNode } from '../types/diagram'
import type { UMLRelationship } from '../types/uml'

const hasSemanticNodeChanges = (changes: NodeChange[]) =>
  changes.some(change => change.type !== 'select' && change.type !== 'dimensions')

const hasSemanticEdgeChanges = (changes: EdgeChange[]) => changes.some(change => change.type !== 'select')

// Initial empty diagram state
export const createEmptyDiagram = (name = 'Untitled Diagram'): UMLDiagram => ({
  id: crypto.randomUUID(),
  name,
  nodes: [],
  edges: [],
  lastModified: new Date(),
  isDirty: false,
})

// Diagram reducer for state management
export const diagramReducer = (state: UMLDiagram, action: DiagramAction): UMLDiagram => {
  switch (action.type) {
    case 'SET_NODES':
      return {
        ...state,
        nodes: action.payload,
        lastModified: new Date(),
        isDirty: true,
      }

    case 'SET_EDGES':
      return {
        ...state,
        edges: action.payload,
        lastModified: new Date(),
        isDirty: true,
      }

    case 'NODE_CHANGES': {
      const areNodeChangesDirty = hasSemanticNodeChanges(action.payload)
      const updatedNodes = applyNodeChanges(action.payload, state.nodes) as UMLNode[]

      // Clean up orphaned edges when nodes are removed
      const hasRemovals = action.payload.some(change => change.type === 'remove')
      let updatedEdges = state.edges
      if (hasRemovals) {
        const nodeIds = new Set(updatedNodes.map(node => node.id))
        updatedEdges = state.edges.filter(edge => nodeIds.has(edge.source) && nodeIds.has(edge.target))
      }

      return {
        ...state,
        nodes: updatedNodes,
        edges: updatedEdges,
        lastModified: areNodeChangesDirty ? new Date() : state.lastModified,
        isDirty: areNodeChangesDirty ? true : state.isDirty,
      }
    }

    case 'EDGE_CHANGES': {
      const areEdgeChangesDirty = hasSemanticEdgeChanges(action.payload)
      return {
        ...state,
        edges: applyEdgeChanges(action.payload, state.edges) as UMLEdge[],
        lastModified: areEdgeChangesDirty ? new Date() : state.lastModified,
        isDirty: areEdgeChangesDirty ? true : state.isDirty,
      }
    }

    case 'ADD_EDGE':
      return {
        ...state,
        edges: addEdge(action.payload, state.edges),
        lastModified: new Date(),
        isDirty: true,
      }

    case 'ADD_NODE': {
      return {
        ...state,
        nodes: [...state.nodes, action.payload],
        lastModified: new Date(),
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
                data: {
                  ...node.data,
                  element: { ...node.data.element, ...updates },
                  isDirty: true,
                },
              }
            : node,
        ) as UMLNode[],
        lastModified: new Date(),
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
                type: updates.type || edge.type, // Update edge type for ReactFlow
                data: {
                  ...edge.data,
                  relationship: { ...edge.data.relationship, ...updates },
                  isDirty: true,
                },
              }
            : edge,
        ),
        lastModified: new Date(),
        isDirty: true,
      }
    }

    case 'DELETE_NODES': {
      const nodesToDelete = new Set(action.payload)
      // Remove nodes and connected edges
      const remainingNodes = state.nodes.filter(node => !nodesToDelete.has(node.id))
      const remainingEdges = state.edges.filter(
        edge => !nodesToDelete.has(edge.source) && !nodesToDelete.has(edge.target),
      )

      return {
        ...state,
        nodes: remainingNodes,
        edges: remainingEdges,
        lastModified: new Date(),
        isDirty: true,
      }
    }

    case 'DELETE_EDGES': {
      const edgesToDelete = new Set(action.payload)
      return {
        ...state,
        edges: state.edges.filter(edge => !edgesToDelete.has(edge.id)),
        lastModified: new Date(),
        isDirty: true,
      }
    }

    case 'SET_VIEWPORT':
      return {
        ...state,
        viewport: action.payload,
      }

    case 'MARK_CLEAN':
      return {
        ...state,
        isDirty: false,
      }

    case 'MARK_DIRTY':
      return {
        ...state,
        isDirty: true,
        lastModified: new Date(),
      }

    case 'LOAD_DIAGRAM':
      return {
        ...action.payload,
        isDirty: false,
      }

    case 'RESET_DIAGRAM':
      return createEmptyDiagram()

    default:
      return state
  }
}

// Helper functions for diagram operations

export const findNodeById = (diagram: UMLDiagram, nodeId: string): UMLNode | undefined => {
  return diagram.nodes.find(node => node.id === nodeId)
}

export const findEdgeById = (diagram: UMLDiagram, edgeId: string): UMLEdge | undefined => {
  return diagram.edges.find(edge => edge.id === edgeId)
}

export const getSelectedNodes = (diagram: UMLDiagram): UMLNode[] => {
  return diagram.nodes.filter(node => node.selected)
}

export const getSelectedEdges = (diagram: UMLDiagram): UMLEdge[] => {
  return diagram.edges.filter(edge => edge.selected)
}

export const getConnectedEdges = (diagram: UMLDiagram, nodeId: string): UMLEdge[] => {
  return diagram.edges.filter(edge => edge.source === nodeId || edge.target === nodeId)
}

export const getElementRelationships = (diagram: UMLDiagram, elementId: string): UMLRelationship[] => {
  return diagram.edges
    .filter(edge => edge.data.relationship.source === elementId || edge.data.relationship.target === elementId)
    .map(edge => edge.data.relationship)
}

// Validation helpers
export const validateConnection = (diagram: UMLDiagram, connection: Connection): boolean => {
  const { source, target } = connection

  // Prevent self-connections
  if (source === target) {
    return false
  }

  // Check if nodes exist
  const sourceNode = findNodeById(diagram, source!)
  const targetNode = findNodeById(diagram, target!)

  if (!sourceNode || !targetNode) {
    return false
  }

  // Check for duplicate connections
  const existingConnection = diagram.edges.find(edge => edge.source === source && edge.target === target)

  if (existingConnection) {
    return false
  }

  // UML-specific validation rules
  // const sourceType = sourceNode.data.element.type
  // const targetType = targetNode.data.element.type
  // TODO: relationship-specific validation like inheritance, realization, etc. ...

  return true
}

// Enhanced validation for specific relationship types
export const validateRelationshipConnection = (
  diagram: UMLDiagram,
  connection: Connection,
  relationshipType: string,
): boolean => {
  if (!validateConnection(diagram, connection)) {
    return false
  }

  const sourceNode = findNodeById(diagram, connection.source!)
  const targetNode = findNodeById(diagram, connection.target!)

  if (!sourceNode || !targetNode) {
    return false
  }

  const sourceType = sourceNode.data.element.type
  const targetType = targetNode.data.element.type

  // Relationship-specific validation rules
  switch (relationshipType) {
    case 'inheritance':
      // Inheritance: class → class/abstractClass only
      return sourceType === 'class' && (targetType === 'class' || targetType === 'abstractClass')

    case 'realization':
      // Realization: class → interface only
      return sourceType === 'class' && targetType === 'interface'

    case 'association':
    case 'aggregation':
    case 'composition':
    case 'dependency':
      // These relationships are more flexible
      return true

    default:
      return true
  }
}

// Diagram serialization
export const serializeDiagram = (diagram: UMLDiagram): string => {
  return JSON.stringify(diagram, null, 2)
}

export const deserializeDiagram = (data: string): UMLDiagram => {
  const parsed = JSON.parse(data)
  return {
    ...parsed,
    lastModified: new Date(parsed.lastModified),
  }
}

// Diagram statistics
export interface DiagramStats {
  nodeCount: number
  edgeCount: number
  elementTypes: Record<string, number>
  relationshipTypes: Record<string, number>
}

export const getDiagramStats = (diagram: UMLDiagram): DiagramStats => {
  const elementTypes: Record<string, number> = {}
  const relationshipTypes: Record<string, number> = {}

  // Count element types
  diagram.nodes.forEach(node => {
    const type = node.data.element.type
    elementTypes[type] = (elementTypes[type] || 0) + 1
  })

  // Count relationship types
  diagram.edges.forEach(edge => {
    const type = edge.data.relationship.type
    relationshipTypes[type] = (relationshipTypes[type] || 0) + 1
  })

  return {
    nodeCount: diagram.nodes.length,
    edgeCount: diagram.edges.length,
    elementTypes,
    relationshipTypes,
  }
}

// Auto-layout helpers (basic grid layout)
export const autoLayoutNodes = (nodes: UMLNode[], spacing = 200): UMLNode[] => {
  const gridSize = Math.ceil(Math.sqrt(nodes.length))

  return nodes.map((node, index) => {
    const row = Math.floor(index / gridSize)
    const col = index % gridSize

    return {
      ...node,
      position: {
        x: col * spacing + 50,
        y: row * spacing + 50,
      },
    }
  })
}
