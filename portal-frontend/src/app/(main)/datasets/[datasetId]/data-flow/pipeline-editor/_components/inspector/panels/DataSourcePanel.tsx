'use client'

/**
 * DataSourcePanel Component
 *
 * Inspector panel for DataSource nodes.
 * Allows selecting a datasource entity and displays its metadata.
 *
 */
import { useTranslations } from 'next-intl'

import { usePermissions } from '@/hooks/use-permissions'
import { PERMISSION_NAMES } from '@/types/currentUser'

import { usePipelineDatasources } from '../../../_hooks/use-pipeline-datasources'
import { datasourceToSelectable } from '../../../_services/entityService'
import type { DataSourceNodeData } from '../../../_types/nodes'
import { EntityMetadata } from '../components/EntityMetadata'
import { EntitySelector } from '../components/EntitySelector'
interface DataSourcePanelProps {
  data: DataSourceNodeData
  onUpdate: (data: Partial<DataSourceNodeData>) => void
}

export const DataSourcePanel: React.FC<DataSourcePanelProps> = ({ data, onUpdate }) => {
  const t = useTranslations('pipelineEditor')
  const { hasPermission } = usePermissions()
  const canReadDatasources = hasPermission(PERMISSION_NAMES.DATASOURCE_READ)
  const { entities, isLoading, isError, getEntityById, getName } = usePipelineDatasources()

  const selectedEntity = data.entityId !== undefined ? getEntityById(data.entityId) : undefined

  const handleEntityChange = (entity: { id: string; name: string } | undefined) => {
    if (entity) {
      const fullEntity = getEntityById(entity.id)
      onUpdate({
        entityId: entity.id,
        configured: true,
        entityMetadata: fullEntity?.connectorType ? { connector: fullEntity.connectorType } : undefined,
      })
    } else {
      onUpdate({
        entityId: undefined,
        entityMetadata: undefined,
        configured: false,
      })
    }
  }

  const selectableEntities = entities.map(datasourceToSelectable)

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
        fallbackName={getName(data.entityId)}
      />

      {selectedEntity && (
        <>
          <EntityMetadata
            title={t('dataSourcePanel.details')}
            items={[{ label: t('dataSourcePanel.connector'), value: selectedEntity.connectorType ?? undefined }]}
          />
          {canReadDatasources && (
            <button
              onClick={() => window.open(`/datasources/${data.entityId}`, '_blank')}
              className="w-full rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
            >
              {t('dataSourcePanel.showDataStructure')}
            </button>
          )}
        </>
      )}
    </div>
  )
}
