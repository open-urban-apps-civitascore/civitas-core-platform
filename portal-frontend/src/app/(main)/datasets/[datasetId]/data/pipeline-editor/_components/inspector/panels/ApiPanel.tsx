'use client'

/**
 * ApiPanel Component
 *
 * Inspector panel for API Request/Response nodes.
 *
 */

import { apiEntityToSelectable, useApiEntities } from '../../../_services/entityService'
import type { ApiNodeData } from '../../../_types/nodes'
import { EntityMetadata } from '../components/EntityMetadata'
import { EntitySelector } from '../components/EntitySelector'

interface ApiPanelProps {
  data: ApiNodeData
  onUpdate: (data: Partial<ApiNodeData>) => void
}

export const ApiPanel: React.FC<ApiPanelProps> = ({ data, onUpdate }) => {
  const { entities, isLoading, isError, getEntityById } = useApiEntities()
  const selectedEntity = data.entityId ? getEntityById(data.entityId) : undefined
  const isRequest = data.nodeType === 'apiRequest'

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
        label={isRequest ? 'API Endpoint' : 'Response Handler'}
        placeholder="Select an API..."
        entities={entities.map(apiEntityToSelectable)}
        selectedId={data.entityId}
        isLoading={isLoading}
        isError={isError}
        onChange={handleEntityChange}
      />
      {selectedEntity && (
        <EntityMetadata
          title="API Details"
          items={[
            { label: 'Method', value: selectedEntity.method },
            { label: 'Path', value: selectedEntity.path },
            { label: 'Description', value: selectedEntity.description },
          ]}
        />
      )}
    </div>
  )
}
