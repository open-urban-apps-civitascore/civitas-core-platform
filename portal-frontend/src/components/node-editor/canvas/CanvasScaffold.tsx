'use client'

import '@xyflow/react/dist/style.css'

import type {
  DefaultEdgeOptions,
  Edge,
  EdgeTypes,
  IsValidConnection,
  Node,
  NodeTypes,
  OnConnect,
  OnEdgesChange,
  OnNodesChange,
} from '@xyflow/react'
import { Background, Controls, ReactFlow, ReactFlowProvider, useReactFlow } from '@xyflow/react'
import type { DragEvent, ReactNode } from 'react'

import { PALETTE_DND_TYPE } from '../palette/PaletteItem'

export interface CanvasScaffoldProps {
  nodes: Node[]
  edges: Edge[]
  nodeTypes: NodeTypes
  edgeTypes?: EdgeTypes
  defaultEdgeOptions?: DefaultEdgeOptions
  onNodesChange?: OnNodesChange
  onEdgesChange?: OnEdgesChange
  onConnect?: OnConnect
  isValidConnection?: IsValidConnection
  onDropNode?: (type: string, position: { x: number; y: number }) => void
  onSelectionChange?: (nodeId: string | null) => void
  children?: ReactNode
}

const CanvasInner = (props: CanvasScaffoldProps) => {
  const { screenToFlowPosition } = useReactFlow()

  const onDragOver = (e: DragEvent) => {
    e.preventDefault()
    e.dataTransfer.dropEffect = 'move'
  }

  const onDrop = (e: DragEvent) => {
    e.preventDefault()
    const type = e.dataTransfer.getData(PALETTE_DND_TYPE)
    if (!type) return
    const position = screenToFlowPosition({ x: e.clientX, y: e.clientY })
    props.onDropNode?.(type, position)
  }

  return (
    <div className="h-full w-full" onDrop={onDrop} onDragOver={onDragOver}>
      <ReactFlow
        nodes={props.nodes}
        edges={props.edges}
        nodeTypes={props.nodeTypes}
        edgeTypes={props.edgeTypes}
        defaultEdgeOptions={props.defaultEdgeOptions}
        onNodesChange={props.onNodesChange}
        onEdgesChange={props.onEdgesChange}
        onConnect={props.onConnect}
        isValidConnection={props.isValidConnection}
        onNodeClick={(_, node) => props.onSelectionChange?.(node.id)}
        onPaneClick={() => props.onSelectionChange?.(null)}
        fitView
        proOptions={{ hideAttribution: true }}
      >
        <Background />
        <Controls />
        {props.children}
      </ReactFlow>
    </div>
  )
}

export const CanvasScaffold = (props: CanvasScaffoldProps) => (
  <ReactFlowProvider>
    <CanvasInner {...props} />
  </ReactFlowProvider>
)
