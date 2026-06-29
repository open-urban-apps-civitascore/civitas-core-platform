'use client'

import '@xyflow/react/dist/style.css'

import type {
  ConnectionMode,
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
  OnSelectionChangeParams,
  Viewport,
} from '@xyflow/react'
import { Background, BackgroundVariant, Controls, ReactFlow, ReactFlowProvider, useReactFlow } from '@xyflow/react'
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
  /** Fired when a node is clicked (id) or the pane is clicked (null). */
  onSelectionChange?: (nodeId: string | null) => void
  /** Raw React Flow selection-change callback (nodes + edges). */
  onReactFlowSelectionChange?: (params: OnSelectionChangeParams) => void
  /** Key(s) that trigger deletion of selected elements. Defaults to ['Delete','Backspace']. */
  deleteKeyCode?: string | string[] | null
  /**
   * When true, the internal Delete/Backspace stop-propagation handler is skipped and React
   * Flow's own deleteKeyCode handles deletion (used by the pipeline editor whose provider
   * reducer reacts to node/edge changes).
   */
  hasNativeDeleteKey?: boolean
  multiSelectionKeyCode?: string | string[] | null
  panActivationKeyCode?: string | string[] | null
  /**
   * When false, the canvas is read-only: nodes can't be moved, connected, deleted or
   * dropped, and the interactive control is hidden. Defaults to true (fully editable).
   */
  canEdit?: boolean
  connectionMode?: ConnectionMode
  shouldSnapToGrid?: boolean
  snapGrid?: [number, number]
  minZoom?: number
  maxZoom?: number
  defaultViewport?: Viewport
  fitViewPadding?: number
  /** Background dot/line grid spacing. */
  backgroundGap?: number
  className?: string
  children?: ReactNode
}

const CanvasInner = (props: CanvasScaffoldProps) => {
  const { screenToFlowPosition, getNodes, getEdges, deleteElements } = useReactFlow()
  const canEdit = props.canEdit ?? true

  const onDeleteSelected = (e: KeyboardEvent<HTMLDivElement>) => {
    if (!canEdit) return
    if (props.hasNativeDeleteKey) return
    if (e.key !== 'Delete' && e.key !== 'Backspace') return
    const selectedNodes = getNodes().filter(n => n.selected)
    const selectedEdges = getEdges().filter(ed => ed.selected)
    if (selectedNodes.length === 0 && selectedEdges.length === 0) return
    e.stopPropagation()
    void deleteElements({ nodes: selectedNodes, edges: selectedEdges })
  }

  const onDragOver = (e: DragEvent) => {
    e.preventDefault()
    e.dataTransfer.dropEffect = canEdit ? 'move' : 'none'
  }

  const onDrop = (e: DragEvent) => {
    e.preventDefault()
    if (!canEdit) return
    const type = e.dataTransfer.getData(PALETTE_DND_TYPE)
    if (!type) return
    const position = screenToFlowPosition({ x: e.clientX, y: e.clientY })
    props.onDropNode?.(type, position)
  }

  return (
    // tabIndex makes the div focusable so onKeyDown fires after clicking inside the canvas

    <div
      className={`h-full w-full ${props.className ?? ''}`}
      onDrop={onDrop}
      onDragOver={onDragOver}
      onKeyDown={onDeleteSelected}
      tabIndex={-1}
    >
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
        onSelectionChange={props.onReactFlowSelectionChange}
        isValidConnection={props.isValidConnection}
        onNodeClick={(_, node) => props.onSelectionChange?.(node.id)}
        onPaneClick={() => props.onSelectionChange?.(null)}
        nodesDraggable={canEdit}
        nodesConnectable={canEdit}
        edgesReconnectable={canEdit}
        connectionMode={props.connectionMode}
        snapToGrid={props.shouldSnapToGrid}
        snapGrid={props.snapGrid}
        minZoom={props.minZoom}
        maxZoom={props.maxZoom}
        defaultViewport={props.defaultViewport}
        deleteKeyCode={canEdit && props.hasNativeDeleteKey ? (props.deleteKeyCode ?? ['Delete', 'Backspace']) : null}
        multiSelectionKeyCode={props.multiSelectionKeyCode}
        panActivationKeyCode={props.panActivationKeyCode}
        fitView
        fitViewOptions={props.fitViewPadding !== undefined ? { padding: props.fitViewPadding } : undefined}
        proOptions={{ hideAttribution: true }}
      >
        <Background variant={BackgroundVariant.Dots} gap={props.backgroundGap} size={1} color="hsl(var(--border))" />
        <Controls showInteractive={canEdit} />
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
