'use client'

/**
 * PipelineCanvas Component
 *
 * Thin wrapper around the shared `CanvasScaffold` (components/node-editor) configured
 * for the pipeline editor. All generic React Flow wiring (drag-drop, delete keys,
 * background, controls, provider) lives in the shared shell; this component only
 * supplies pipeline-specific state and callbacks.
 *
 */

import {
  type Connection,
  ConnectionMode,
  type EdgeChange,
  type NodeChange,
  type OnSelectionChangeParams,
} from '@xyflow/react'
import { useTranslations } from 'next-intl'
import { useCallback, useMemo } from 'react'

import { CanvasScaffold } from '@/components/node-editor/canvas/CanvasScaffold'

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
 * Wraps the shared CanvasScaffold with pipeline-specific configuration.
 *
 */
export const PipelineCanvas: React.FC<PipelineCanvasProps> = ({ className = '' }) => {
  const t = useTranslations('pipelineEditor')
  const { pipeline, dispatch, addNode, addEdge, validateConnection, hideValidationPanel } = useActivePipeline()

  const nodeTypes = useMemo(() => pipelineNodeTypes, [])
  const edgeTypes = useMemo(() => pipelineEdgeTypes, [])

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => dispatch({ type: 'NODE_CHANGES', payload: changes }),
    [dispatch],
  )

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => dispatch({ type: 'EDGE_CHANGES', payload: changes }),
    [dispatch],
  )

  const onConnect = useCallback(
    (connection: Connection) => {
      if (validateConnection(connection)) addEdge(connection)
    },
    [validateConnection, addEdge],
  )

  const onDropNode = useCallback(
    (nodeType: string, position: { x: number; y: number }) => {
      addNode({ nodeType: nodeType as PipelineNodeType, position })
    },
    [addNode],
  )

  // Hide validation panel when the user makes a selection (latest action wins).
  const onReactFlowSelectionChange = useCallback(
    ({ nodes, edges }: OnSelectionChangeParams) => {
      if (nodes.length > 0 || edges.length > 0) hideValidationPanel()
    },
    [hideValidationPanel],
  )

  if (!pipeline) {
    return (
      <div className={`flex h-full w-full items-center justify-center ${className}`}>
        <p className="text-muted-foreground">{t('canvas.noPipeline')}</p>
      </div>
    )
  }

  return (
    <CanvasScaffold
      nodes={pipeline.nodes}
      edges={pipeline.edges}
      nodeTypes={nodeTypes}
      edgeTypes={edgeTypes}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onConnect={onConnect}
      onDropNode={onDropNode}
      onReactFlowSelectionChange={onReactFlowSelectionChange}
      isValidConnection={connection => validateConnection(connection as Connection)}
      connectionMode={ConnectionMode.Loose}
      shouldSnapToGrid={CANVAS_CONFIG.snapToGrid}
      snapGrid={[CANVAS_CONFIG.gridSize, CANVAS_CONFIG.gridSize]}
      minZoom={CANVAS_CONFIG.minZoom}
      maxZoom={CANVAS_CONFIG.maxZoom}
      defaultViewport={CANVAS_CONFIG.defaultViewport}
      fitViewPadding={CANVAS_CONFIG.fitViewPadding}
      backgroundGap={CANVAS_CONFIG.gridSize}
      multiSelectionKeyCode={['Meta', 'Ctrl']}
      panActivationKeyCode={null}
      hasNativeDeleteKey
      deleteKeyCode={['Delete', 'Backspace']}
      className={`bg-muted/10 ${className}`}
    />
  )
}
