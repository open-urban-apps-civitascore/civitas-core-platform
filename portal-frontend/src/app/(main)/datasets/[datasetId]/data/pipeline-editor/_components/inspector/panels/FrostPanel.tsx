'use client'

/**
 * FrostPanel Component
 *
 * Inspector panel for FROST storage nodes.
 * Displays the fixed FROST server configuration (read-only).
 *
 */

import type { FrostNodeData } from '../../../_types/nodes'
import { EntityMetadata } from '../components/EntityMetadata'

interface FrostPanelProps {
  data: FrostNodeData
}

export const FrostPanel: React.FC<FrostPanelProps> = ({ data }) => {
  return (
    <div className="space-y-4 p-4">
      <EntityMetadata
        title="FROST Server Details"
        items={[
          { label: 'Server Name', value: data.serverName },
          { label: 'Server URL', value: data.serverUrl },
          { label: 'Version', value: data.version },
        ]}
      />
    </div>
  )
}
