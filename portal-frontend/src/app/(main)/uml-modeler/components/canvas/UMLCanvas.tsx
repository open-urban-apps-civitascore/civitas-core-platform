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
  useReactFlow,
} from '@xyflow/react'
import { useCallback, useMemo } from 'react'

import { useUMLDiagram } from '../../hooks/UMLDiagramContext'
import type { UMLElementType } from '../../types/uml'
import { edgeTypes as umlEdgeTypes } from '../edges/edgeTypes'
import { UMLMarkers } from '../edges/UMLMarkers'
import { nodeTypes as umlNodeTypes } from '../nodes/nodeTypes'

interface UMLCanvasProps {
  className?: string
}

export const UMLCanvas: React.FC<UMLCanvasProps> = ({ className = '' }) => {
  const { diagram, dispatch, addEdge, validateConnection, addNode } = useUMLDiagram()
  const { screenToFlowPosition } = useReactFlow()

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

  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
  }, [])

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault()

      const elementType = event.dataTransfer.getData('application/reactflow') as UMLElementType

      if (!elementType) {
        return
      }

      const position = screenToFlowPosition({
        x: event.clientX,
        y: event.clientY,
      })

      const NodeCreationContext = {
        elementType,
        position,
      }

      addNode(NodeCreationContext)
    },
    [screenToFlowPosition, addNode],
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
        onDrop={onDrop}
        onDragOver={onDragOver}
        connectionMode={ConnectionMode.Loose}
        fitView
        fitViewOptions={{ padding: 0.1 }}
        className="bg-gray-50"
        deleteKeyCode={['Delete', 'Backspace']}
        multiSelectionKeyCode={['Meta', 'Ctrl']}
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="#464646" />
        <Controls position="bottom-left" showZoom showFitView showInteractive />
      </ReactFlow>
    </div>
  )
}
