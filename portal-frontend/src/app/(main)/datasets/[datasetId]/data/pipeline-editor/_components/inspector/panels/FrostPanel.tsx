'use client'

/**
 * FrostPanel Component
 *
 * Inspector panel for FROST storage nodes.
 *
 */

import { frostEntityToSelectable, useFrostEntities } from '../../../_services/entityService'
import type { FrostNodeData } from '../../../_types/nodes'
import { EntityMetadata } from '../components/EntityMetadata'
import { EntitySelector } from '../components/EntitySelector'

interface FrostPanelProps {
  data: FrostNodeData
  onUpdate: (data: Partial<FrostNodeData>) => void
}

export const FrostPanel: React.FC<FrostPanelProps> = ({ data, onUpdate }) => {
  const { entities, isLoading, isError, getEntityById } = useFrostEntities()
  const selectedEntity = data.entityId ? getEntityById(data.entityId) : undefined

  const handleEntityChange = (entity: { id: string | number; name: string } | undefined) => {
    if (entity) {
      onUpdate({ entityId: String(entity.id), entityName: entity.name, configured: true })
    } else {
      onUpdate({ entityId: undefined, entityName: undefined, configured: false })
    }
  }

  return (
    <div className="space-y-4 p-4">
      <EntitySelector
        label="FROST Server"
        placeholder="Select a FROST server..."
        entities={entities.map(frostEntityToSelectable)}
        selectedId={data.entityId}
        isLoading={isLoading}
        isError={isError}
        onChange={handleEntityChange}
      />
      {selectedEntity && (
        <EntityMetadata
          title="FROST Server Details"
          items={[
            { label: 'Server URL', value: selectedEntity.serverUrl },
            { label: 'Version', value: selectedEntity.version },
            { label: 'Description', value: selectedEntity.description },
          ]}
        />
      )}
    </div>
  )
}
