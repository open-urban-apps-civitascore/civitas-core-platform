'use client'

/**
 * ApiPanel Component
 *
 * Inspector panel for API Request/Response nodes.
 * Displays the auto-configured API path (read-only).
 *
 */

import { useTranslations } from 'next-intl'

import type { ApiNodeData } from '../../../_types/nodes'
import { EntityMetadata } from '../components/EntityMetadata'

interface ApiPanelProps {
  data: ApiNodeData
}

export const ApiPanel: React.FC<ApiPanelProps> = ({ data }) => {
  const t = useTranslations('pipelineEditor')
  const isRequest = data.nodeType === 'apiRequest'

  return (
    <div className="space-y-4 p-4">
      <EntityMetadata
        title={isRequest ? t('apiPanel.requestDetails') : t('apiPanel.responseDetails')}
        items={[{ label: t('apiPanel.apiPath'), value: data.apiPath }]}
      />
    </div>
  )
}
