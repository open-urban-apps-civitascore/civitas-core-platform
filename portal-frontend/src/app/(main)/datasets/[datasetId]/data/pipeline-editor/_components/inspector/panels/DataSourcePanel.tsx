'use client'

/**
 * DataSourcePanel Component
 *
 * Inspector panel for DataSource nodes.
 * Allows selecting a datasource entity and displays its metadata.
 *
 */

import { datasourceToSelectable, useDataSourceEntities } from '../../../_services/entityService'
import type { DataSourceNodeData } from '../../../_types/nodes'
import { EntityMetadata } from '../components/EntityMetadata'
import { EntitySelector } from '../components/EntitySelector'

// ============================================================================
// Props
// ============================================================================

interface DataSourcePanelProps {
  data: DataSourceNodeData
  onUpdate: (data: Partial<DataSourceNodeData>) => void
}

// ============================================================================
// Component
// ============================================================================

export const DataSourcePanel: React.FC<DataSourcePanelProps> = ({ data, onUpdate }) => {
  const { entities, isLoading, isError, getEntityById } = useDataSourceEntities()

  const selectedEntity = data.entityId !== undefined ? getEntityById(data.entityId) : undefined

  const handleEntityChange = (entity: { id: string | number; name: string } | undefined) => {
    if (entity) {
      onUpdate({
        entityId: typeof entity.id === 'string' ? parseInt(entity.id, 10) : entity.id,
        entityName: entity.name,
        configured: true,
      })
    } else {
      onUpdate({
        entityId: undefined,
        entityName: undefined,
        configured: false,
      })
    }
  }

  const selectableEntities = entities.map(datasourceToSelectable)

  return (
    <div className="space-y-4 p-4">
      <EntitySelector
        label="DataSource"
        placeholder="Select a datasource..."
        entities={selectableEntities}
        selectedId={data.entityId}
        isLoading={isLoading}
        isError={isError}
        onChange={handleEntityChange}
      />

      {selectedEntity && (
        <>
          <EntityMetadata
            title="DataSource Details"
            items={[
              { label: 'Connector', value: selectedEntity.connector },
              { label: 'Connection', value: selectedEntity.connection },
              { label: 'Status', value: selectedEntity.status },
              { label: 'Description', value: selectedEntity.description },
              { label: 'Tags', value: selectedEntity.tags },
            ]}
          />
          <button
            onClick={() => window.open(`/datasources/${data.entityId}`, '_blank')}
            className="w-full rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
          >
            Show Data Structure
          </button>
        </>
      )}
    </div>
  )
}
