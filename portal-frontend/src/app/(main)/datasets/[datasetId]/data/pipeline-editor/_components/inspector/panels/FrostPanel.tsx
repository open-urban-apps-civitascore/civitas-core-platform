'use client'

/**
 * FrostPanel Component
 *
 * Inspector panel for FROST storage nodes.
 * Displays the fixed FROST server configuration (read-only).
 *
 */

import { useTranslations } from 'next-intl'

import type { FrostNodeData } from '../../../_types/nodes'
import { EntityMetadata } from '../components/EntityMetadata'

interface FrostPanelProps {
  data: FrostNodeData
}

export const FrostPanel: React.FC<FrostPanelProps> = ({ data }) => {
  const t = useTranslations('datastructures.pipelineEditor')

  return (
    <div className="space-y-4 p-4">
      <EntityMetadata
        title={t('frostPanel.details')}
        items={[
          { label: t('frostPanel.serverName'), value: data.serverName },
          { label: t('frostPanel.serverUrl'), value: data.serverUrl },
          { label: t('frostPanel.version'), value: data.version },
        ]}
      />
    </div>
  )
}
