'use client'

/**
 * ControlPanel Component
 *
 * Inspector panel for Start and End control nodes.
 * Displays static information about these nodes.
 *
 */

import { Info } from 'lucide-react'
import { useTranslations } from 'next-intl'

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
  const t = useTranslations('datastructures.pipelineEditor')
  const isStart = data.nodeType === 'start'

  return (
    <div className="space-y-4 p-4">
      <div className="flex items-start gap-3 rounded-md bg-muted/50 p-3">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
        <div className="space-y-1">
          <p className="text-sm font-medium text-foreground">
            {isStart ? t('controlPanel.entryPoint') : t('controlPanel.exitPoint')}
          </p>
          <p className="text-sm text-muted-foreground">
            {isStart ? t('controlPanel.entryPointDesc') : t('controlPanel.exitPointDesc')}
          </p>
        </div>
      </div>

      <div className="space-y-2">
        <h4 className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
          {t('controlPanel.nodeInformation')}
        </h4>
        <dl className="space-y-2">
          <div className="flex justify-between">
            <dt className="text-sm text-muted-foreground">{t('controlPanel.type')}</dt>
            <dd className="text-sm font-medium">{isStart ? t('nodeTypes.start') : t('nodeTypes.end')}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-sm text-muted-foreground">{t('controlPanel.configurable')}</dt>
            <dd className="text-sm font-medium">{t('controlPanel.no')}</dd>
          </div>
        </dl>
      </div>
    </div>
  )
}
