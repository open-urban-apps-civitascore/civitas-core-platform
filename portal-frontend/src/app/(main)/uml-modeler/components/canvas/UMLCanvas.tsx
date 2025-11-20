'use client'

import {
  Background,
  BackgroundVariant,
  type Connection,
  ConnectionMode,
  Controls,
  type EdgeChange,
  type NodeChange,
  ReactFlow,
} from '@xyflow/react'
import { useCallback, useMemo } from 'react'

import { useUMLDiagram } from '../../hooks/UMLDiagramContext'
import { edgeTypes as umlEdgeTypes } from '../edges/edgeTypes'
import { UMLMarkers } from '../edges/UMLMarkers'
import { nodeTypes as umlNodeTypes } from '../nodes/nodeTypes'

interface UMLCanvasProps {
  className?: string
}

export const UMLCanvas: React.FC<UMLCanvasProps> = ({ className = '' }) => {
  const { diagram, dispatch, addEdge, validateConnection } = useUMLDiagram()

  // Node types registry - UML node components
  const nodeTypes = useMemo(() => umlNodeTypes, [])

  // Edge types registry - UML edge components
  const edgeTypes = useMemo(() => umlEdgeTypes, [])

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => {
      dispatch({ type: 'NODE_CHANGES', payload: changes })
    },
    [dispatch],
  )

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      dispatch({ type: 'EDGE_CHANGES', payload: changes })
    },
    [dispatch],
  )

  const onConnect = useCallback(
    (connection: Connection) => {
      if (validateConnection(connection)) {
        addEdge(connection)
      }
    },
    [validateConnection, addEdge],
  )

  return (
    <div className={`h-full w-full ${className}`}>
      <UMLMarkers />
      <ReactFlow
        nodes={diagram.nodes}
        edges={diagram.edges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        connectionMode={ConnectionMode.Loose}
        fitView
        fitViewOptions={{ padding: 0.1 }}
        className="bg-gray-50"
        deleteKeyCode={['Delete', 'Backspace']}
        multiSelectionKeyCode={['Meta', 'Ctrl']}
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="#e2e8f0" />
        <Controls position="bottom-left" showZoom showFitView showInteractive />
      </ReactFlow>
    </div>
  )
}
