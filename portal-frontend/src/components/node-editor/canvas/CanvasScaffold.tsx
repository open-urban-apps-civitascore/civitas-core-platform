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
  OnConnectEnd,
  OnConnectStart,
  OnEdgesChange,
  OnNodesChange,
} from '@xyflow/react'
import { Background, Controls, ReactFlow, ReactFlowProvider, useReactFlow } from '@xyflow/react'
import type { DragEvent, KeyboardEvent, ReactNode } from 'react'

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
  onConnectStart?: OnConnectStart
  onConnectEnd?: OnConnectEnd
  isValidConnection?: IsValidConnection
  onDropNode?: (type: string, position: { x: number; y: number }) => void
  onSelectionChange?: (nodeId: string | null) => void
  /** Key(s) that trigger deletion of selected elements. Defaults to ['Delete','Backspace']. */
  deleteKeyCode?: string | string[] | null
  children?: ReactNode
}

const CanvasInner = (props: CanvasScaffoldProps) => {
  const { screenToFlowPosition, getNodes, getEdges, deleteElements } = useReactFlow()

  const onDeleteSelected = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key !== 'Delete' && e.key !== 'Backspace') return
    const selectedNodes = getNodes().filter(n => n.selected)
    const selectedEdges = getEdges().filter(ed => ed.selected)
    if (selectedNodes.length === 0 && selectedEdges.length === 0) return
    e.stopPropagation()
    void deleteElements({ nodes: selectedNodes, edges: selectedEdges })
  }

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
    // tabIndex makes the div focusable so onKeyDown fires after clicking inside the canvas
    // eslint-disable-next-line jsx-a11y/no-static-element-interactions
    <div className="h-full w-full" onDrop={onDrop} onDragOver={onDragOver} onKeyDown={onDeleteSelected} tabIndex={-1}>
      <ReactFlow
        nodes={props.nodes}
        edges={props.edges}
        nodeTypes={props.nodeTypes}
        edgeTypes={props.edgeTypes}
        defaultEdgeOptions={props.defaultEdgeOptions}
        onNodesChange={props.onNodesChange}
        onEdgesChange={props.onEdgesChange}
        onConnect={props.onConnect}
        onConnectStart={props.onConnectStart}
        onConnectEnd={props.onConnectEnd}
        isValidConnection={props.isValidConnection}
        onNodeClick={(_, node) => props.onSelectionChange?.(node.id)}
        onPaneClick={() => props.onSelectionChange?.(null)}
        deleteKeyCode={null}
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
