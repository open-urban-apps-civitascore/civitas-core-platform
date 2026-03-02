'use client'

/**
 * PipelineInspector Component
 *
 * Right sidebar for node configuration and property editing.
 * Routes to appropriate panel based on selected node type.
 * Shows validation results when user clicks Validate.
 *
 */

import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useRef, useState } from 'react'

import { LAYOUT_DIMENSIONS } from '../../_constants/pipelineStyles'
import { useActivePipeline } from '../../_hooks/use-active-pipeline'
import type { PipelineNodeData } from '../../_types/nodes'
import {
  isApiNodeData,
  isControlNodeData,
  isCronNodeData,
  isDataSourceNodeData,
  isFrostNodeData,
  isMappingNodeData,
} from '../../_types/nodes'
import { InspectorHeader } from './components/InspectorHeader'
import { ApiPanel } from './panels/ApiPanel'
import { ControlPanel } from './panels/ControlPanel'
import { CronPanel } from './panels/CronPanel'
import { DataSourcePanel } from './panels/DataSourcePanel'
import { FrostPanel } from './panels/FrostPanel'
import { MappingPanel } from './panels/MappingPanel'
import { ValidationPanel } from './validation'

// ============================================================================
// Props
// ============================================================================

interface PipelineInspectorProps {
  className?: string
}

// ============================================================================
// Component
// ============================================================================

export const PipelineInspector: React.FC<PipelineInspectorProps> = ({ className = '' }) => {
  const t = useTranslations('pipelineEditor')
  const { selectedNode, selectedEdge, updateNode, shouldShowValidationPanel, validationResult } = useActivePipeline()

  // Resizable width state
  const [width, setWidth] = useState<number>(LAYOUT_DIMENSIONS.inspectorWidth)
  const isResizing = useRef(false)

  // Handle resize drag
  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!isResizing.current) return

      // Calculate new width (drag from left edge, so subtract from window width)
      const newWidth = window.innerWidth - e.clientX
      const clampedWidth = Math.max(
        LAYOUT_DIMENSIONS.inspectorMinWidth,
        Math.min(LAYOUT_DIMENSIONS.inspectorMaxWidth, newWidth),
      )
      setWidth(clampedWidth)
    }

    const handleMouseUp = () => {
      isResizing.current = false
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }

    document.addEventListener('mousemove', handleMouseMove)
    document.addEventListener('mouseup', handleMouseUp)

    return () => {
      document.removeEventListener('mousemove', handleMouseMove)
      document.removeEventListener('mouseup', handleMouseUp)
    }
  }, [])

  const handleResizeStart = useCallback((e: React.MouseEvent) => {
    e.preventDefault()
    isResizing.current = true
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }, [])

  const handleNodeUpdate = useCallback(
    (data: Partial<PipelineNodeData>) => {
      if (selectedNode) {
        updateNode(selectedNode.id, data)
      }
    },
    [selectedNode, updateNode],
  )

  // Render the appropriate panel based on node type
  const renderNodePanel = () => {
    if (!selectedNode?.data) return null

    const { data } = selectedNode

    if (isControlNodeData(data)) {
      return <ControlPanel data={data} />
    }
    if (isDataSourceNodeData(data)) {
      return <DataSourcePanel data={data} onUpdate={handleNodeUpdate} />
    }
    if (isApiNodeData(data)) {
      return <ApiPanel data={data} />
    }
    if (isCronNodeData(data)) {
      return <CronPanel data={data} onUpdate={handleNodeUpdate} />
    }
    if (isFrostNodeData(data)) {
      return <FrostPanel data={data} />
    }
    if (isMappingNodeData(data)) {
      return <MappingPanel data={data} onUpdate={handleNodeUpdate} />
    }

    return null
  }

  return (
    <div
      className={`relative flex flex-shrink-0 flex-col overflow-hidden border-l border-border bg-background ${className}`}
      style={{ width }}
    >
      {/* Resize handle */}
      <div
        onMouseDown={handleResizeStart}
        className="absolute left-0 top-0 z-10 h-full w-1 cursor-col-resize hover:bg-primary/50"
      />
      {/* Show validation panel when shouldShowValidationPanel is true (overrides node selection) */}
      {shouldShowValidationPanel && validationResult ? (
        <ValidationPanel validationResult={validationResult} />
      ) : selectedNode ? (
        <>
          <InspectorHeader
            nodeType={selectedNode.type}
            label={selectedNode.data.label as string}
            isConfigured={selectedNode.data.configured as boolean}
          />
          <div className="flex-1 overflow-y-auto">{renderNodePanel()}</div>
        </>
      ) : selectedEdge ? (
        <>
          <div className="border-b border-border px-3 py-2">
            <h3 className="text-sm font-medium text-foreground">{t('inspector.edgeProperties')}</h3>
          </div>
          <div className="flex-1 overflow-y-auto p-4">
            <div className="rounded-md border border-border bg-muted/30 p-3">
              <dl className="space-y-2">
                <div className="flex justify-between">
                  <dt className="text-sm text-muted-foreground">{t('inspector.source')}</dt>
                  <dd className="text-sm font-medium">{selectedEdge.source}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-sm text-muted-foreground">{t('inspector.target')}</dt>
                  <dd className="text-sm font-medium">{selectedEdge.target}</dd>
                </div>
              </dl>
            </div>
          </div>
        </>
      ) : (
        <>
          <div className="border-b border-border px-3 py-2">
            <h3 className="text-sm font-medium text-foreground">{t('inspector.properties')}</h3>
          </div>
          <div className="flex flex-1 flex-col items-center justify-center p-4 text-center">
            <p className="text-sm text-muted-foreground">{t('inspector.noNodeSelected')}</p>
            <p className="mt-1 text-xs text-muted-foreground">{t('inspector.noNodeSelectedHint')}</p>
          </div>
        </>
      )}
    </div>
  )
}
