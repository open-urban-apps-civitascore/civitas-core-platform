'use client'

/**
 * PipelineCanvas Component
 *
 * React Flow canvas for visual pipeline editing.
 * Handles drag-and-drop from palette, node selection, and edge creation.
 * Adapted from UML modeler's UMLCanvas.tsx
 *
 */

import '@xyflow/react/dist/style.css'

import {
  Background,
  BackgroundVariant,
  type Connection,
  ConnectionMode,
  Controls,
  type EdgeChange,
  type NodeChange,
  type OnSelectionChangeParams,
  ReactFlow,
  useReactFlow,
} from '@xyflow/react'
import { useTranslations } from 'next-intl'
import { useCallback, useMemo } from 'react'

import { CANVAS_CONFIG } from '../../_constants/pipelineStyles'
import { useActivePipeline } from '../../_hooks/use-active-pipeline'
import type { PipelineNodeType } from '../../_types/pipeline'
import { pipelineEdgeTypes } from '../edges/edgeTypes'
import { pipelineNodeTypes } from '../nodes/nodeTypes'

// ============================================================================
// Props
// ============================================================================

interface PipelineCanvasProps {
  className?: string
}

// ============================================================================
// Component
// ============================================================================

/**
 * Pipeline canvas component.
 * Wraps React Flow with pipeline-specific configuration.
 *
 */
export const PipelineCanvas: React.FC<PipelineCanvasProps> = ({ className = '' }) => {
  const t = useTranslations('datastructures.pipelineEditor')
  const { pipeline, dispatch, addNode, addEdge, validateConnection, hideValidationPanel } = useActivePipeline()
  const { screenToFlowPosition } = useReactFlow()

  // Node types registry - maps node type strings to React components
  const nodeTypes = useMemo(() => pipelineNodeTypes, [])

  // Edge types registry - custom edge styling
  const edgeTypes = useMemo(() => pipelineEdgeTypes, [])

  /**
   * Handles node changes from React Flow (position, selection, etc.)
   */
  const onNodesChange = useCallback(
    (changes: NodeChange[]) => {
      dispatch({ type: 'NODE_CHANGES', payload: changes })
    },
    [dispatch],
  )

  /**
   * Handles edge changes from React Flow (selection, removal, etc.)
   */
  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      dispatch({ type: 'EDGE_CHANGES', payload: changes })
    },
    [dispatch],
  )

  /**
   * Handles new connections between nodes.
   */
  const onConnect = useCallback(
    (connection: Connection) => {
      if (validateConnection(connection)) {
        addEdge(connection)
      }
    },
    [validateConnection, addEdge],
  )

  /**
   * Handles drag over event for drop zone.
   */
  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
  }, [])

  /**
   * Handles drop event from palette.
   * Creates a new node at the drop position.
   */
  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault()

      const nodeType = event.dataTransfer.getData('application/reactflow') as PipelineNodeType

      if (!nodeType) {
        return
      }

      // Convert screen coordinates to flow coordinates
      const position = screenToFlowPosition({
        x: event.clientX,
        y: event.clientY,
      })

      // Add the new node
      addNode({
        nodeType,
        position,
      })
    },
    [screenToFlowPosition, addNode],
  )

  /**
   * Handles selection changes from React Flow.
   * Hides validation panel when user selects a node/edge (latest action wins).
   */
  const onSelectionChange = useCallback(
    ({ nodes, edges }: OnSelectionChangeParams) => {
      // Hide validation panel when user makes a selection (latest action wins)
      if (nodes.length > 0 || edges.length > 0) {
        hideValidationPanel()
      }
    },
    [hideValidationPanel],
  )

  // Show loading state if no pipeline
  if (!pipeline) {
    return (
      <div className={`flex h-full w-full items-center justify-center ${className}`}>
        <p className="text-muted-foreground">{t('canvas.noPipeline')}</p>
      </div>
    )
  }

  return (
    <div className={`h-full w-full ${className}`}>
      <ReactFlow
        nodes={pipeline.nodes}
        edges={pipeline.edges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onSelectionChange={onSelectionChange}
        onConnect={onConnect}
        onDrop={onDrop}
        onDragOver={onDragOver}
        connectionMode={ConnectionMode.Loose}
        fitView
        fitViewOptions={{ padding: CANVAS_CONFIG.fitViewPadding }}
        snapToGrid={CANVAS_CONFIG.snapToGrid}
        snapGrid={[CANVAS_CONFIG.gridSize, CANVAS_CONFIG.gridSize]}
        minZoom={CANVAS_CONFIG.minZoom}
        maxZoom={CANVAS_CONFIG.maxZoom}
        defaultViewport={CANVAS_CONFIG.defaultViewport}
        deleteKeyCode={['Delete', 'Backspace']}
        multiSelectionKeyCode={['Meta', 'Ctrl']}
        className="bg-muted/10"
      >
        <Background variant={BackgroundVariant.Dots} gap={CANVAS_CONFIG.gridSize} size={1} color="hsl(var(--border))" />
        <Controls position="bottom-left" showZoom showFitView showInteractive />
      </ReactFlow>
    </div>
  )
}
