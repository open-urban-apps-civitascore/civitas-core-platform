'use client'

/**
 * DataSourcePanel Component
 *
 * Inspector panel for DataSource nodes.
 * Allows selecting a datasource entity and displays its metadata.
 *
 */
import { useTranslations } from 'next-intl'

import { DATASOURCE_STATUS_TYPES } from '@/types/datasources'

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
  const t = useTranslations('pipelineEditor')
  const { entities, isLoading, isError, getEntityById } = useDataSourceEntities()

  const selectedEntity = data.entityId !== undefined ? getEntityById(data.entityId) : undefined

  const handleEntityChange = (entity: { id: string; name: string } | undefined) => {
    if (entity) {
      onUpdate({
        entityId: entity.id,
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

  const selectableEntities = entities
    .filter(entity => entity.dataSourceStatus === DATASOURCE_STATUS_TYPES.AVAILABLE)
    .map(datasourceToSelectable)

  return (
    <div className="space-y-4 p-4">
      <EntitySelector
        label={t('dataSourcePanel.label')}
        placeholder={t('dataSourcePanel.placeholder')}
        entities={selectableEntities}
        selectedId={data.entityId}
        isLoading={isLoading}
        isError={isError}
        onChange={handleEntityChange}
      />

      {selectedEntity && (
        <>
          <EntityMetadata
            title={t('dataSourcePanel.details')}
            items={[
              { label: t('dataSourcePanel.connector'), value: selectedEntity.connectorType ?? undefined },
              { label: t('dataSourcePanel.status'), value: selectedEntity.dataSourceStatus },
              { label: t('dataSourcePanel.description'), value: selectedEntity.description ?? undefined },
            ]}
          />
          <button
            onClick={() => window.open(`/datasources/${data.entityId}`, '_blank')}
            className="w-full rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
          >
            {t('dataSourcePanel.showDataStructure')}
          </button>
        </>
      )}
    </div>
  )
}
