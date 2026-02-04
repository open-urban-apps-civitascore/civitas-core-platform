'use client'

/**
 * ControlPanel Component
 *
 * Inspector panel for Start and End control nodes.
 * Displays static information about these nodes.
 *
 */

import { Info } from 'lucide-react'

import type { ControlNodeData } from '../../../_types/nodes'

// ============================================================================
// Props
// ============================================================================

interface ControlPanelProps {
  data: ControlNodeData
}

// ============================================================================
// Component
// ============================================================================

export const ControlPanel: React.FC<ControlPanelProps> = ({ data }) => {
  const isStart = data.nodeType === 'start'

  return (
    <div className="space-y-4 p-4">
      <div className="flex items-start gap-3 rounded-md bg-muted/50 p-3">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
        <div className="space-y-1">
          <p className="text-sm font-medium text-foreground">
            {isStart ? 'Pipeline Entry Point' : 'Pipeline Exit Point'}
          </p>
          <p className="text-sm text-muted-foreground">
            {isStart
              ? 'The Start node marks where the pipeline execution begins. Connect it to a trigger or data source node to define how the pipeline is activated.'
              : 'The End node marks where the pipeline execution completes. All data flow paths should eventually lead to this node.'}
          </p>
        </div>
      </div>

      <div className="space-y-2">
        <h4 className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Node Information</h4>
        <dl className="space-y-2">
          <div className="flex justify-between">
            <dt className="text-sm text-muted-foreground">Type</dt>
            <dd className="text-sm font-medium">{isStart ? 'Start' : 'End'}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-sm text-muted-foreground">Configurable</dt>
            <dd className="text-sm font-medium">No</dd>
          </div>
        </dl>
      </div>
    </div>
  )
}
