'use client'

/**
 * PipelineInspector Component
 *
 * Right sidebar for node configuration and property editing.
 * Uses the shared InspectorShell for chrome (resize, header, empty state);
 * routes to the appropriate domain panel based on the selected node's registry entry.
 * Shows validation results when the user clicks Validate.
 *
 */

import { useTranslations } from 'next-intl'
import { useCallback } from 'react'

import { InspectorShell } from '@/components/node-editor/inspector/InspectorShell'

import { getNodeDefForData } from '../../_config/nodeRegistry'
import { LAYOUT_DIMENSIONS } from '../../_constants/pipelineStyles'
import { useActivePipeline } from '../../_hooks/use-active-pipeline'
import { useReadOnly } from '../../_hooks/use-pipeline-read-only'
import type { PipelineNodeData } from '../../_types/nodes'
import { InspectorHeader } from './components/InspectorHeader'
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
  const { isReadOnly } = useReadOnly()

  const handleNodeUpdate = useCallback(
    (data: Partial<PipelineNodeData>) => {
      if (isReadOnly) return
      if (selectedNode) {
        updateNode(selectedNode.id, data)
      }
    },
    [isReadOnly, selectedNode, updateNode],
  )

  // Render the appropriate panel by looking up the node's registry definition.
  // Adding a new node type requires no change here — only a new registry entry.
  const renderNodePanel = () => {
    if (!selectedNode?.data) return null

    const def = getNodeDefForData(selectedNode.data)
    if (!def) return null

    const { InspectorPanel, panelReadonly } = def
    const panel = <InspectorPanel data={selectedNode.data} onUpdate={handleNodeUpdate} />

    // Display-only panels stay fully legible. Editable panels are dimmed and made
    // non-interactive in read-only mode; handleNodeUpdate additionally no-ops as a safeguard.
    if (panelReadonly || !isReadOnly) return panel

    return (
      <div className="pointer-events-none opacity-60" aria-disabled>
        {panel}
      </div>
    )
  }

  const shellWidthProps = {
    initialWidth: LAYOUT_DIMENSIONS.inspectorWidth,
    minWidth: LAYOUT_DIMENSIONS.inspectorMinWidth,
    maxWidth: LAYOUT_DIMENSIONS.inspectorMaxWidth,
    className,
  }

  // Validation panel takes precedence over node/edge selection.
  if (shouldShowValidationPanel && validationResult) {
    return (
      <InspectorShell title={t('validation.results')} {...shellWidthProps}>
        <ValidationPanel validationResult={validationResult} />
      </InspectorShell>
    )
  }

  if (selectedNode) {
    return (
      <InspectorShell
        title={t('inspector.properties')}
        header={
          <InspectorHeader
            nodeType={selectedNode.type}
            label={selectedNode.data.label as string}
            isConfigured={selectedNode.data.configured as boolean}
          />
        }
        {...shellWidthProps}
      >
        {renderNodePanel()}
      </InspectorShell>
    )
  }

  if (selectedEdge) {
    return (
      <InspectorShell title={t('inspector.edgeProperties')} {...shellWidthProps}>
        <div className="p-4">
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
      </InspectorShell>
    )
  }

  return (
    <InspectorShell
      title={t('inspector.properties')}
      isEmpty
      emptyMessage={t('inspector.noNodeSelected')}
      {...shellWidthProps}
    />
  )
}
