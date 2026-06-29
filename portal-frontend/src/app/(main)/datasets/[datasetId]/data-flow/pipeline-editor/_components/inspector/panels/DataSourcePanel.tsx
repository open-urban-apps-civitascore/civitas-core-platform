'use client'

/**
 * DataSourcePanel Component
 *
 * Inspector panel for DataSource nodes.
 * Allows selecting a datasource entity and displays its metadata.
 *
 */
import { useParams } from 'next/navigation'
import { useTranslations } from 'next-intl'

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import { usePermissions } from '@/hooks/use-permissions'
import { PERMISSION_NAMES } from '@/types/currentUser'

import { datasourceToSelectable, useDataSourceEntities } from '../../../_services/entityService'
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
  const { datasetId } = useParams<{ datasetId: string }>()
  const {
    data: datasetResponse,
    isLoading: isDatasetLoading,
    isError: isDatasetError,
  } = useGetDataset({ id: datasetId })
  const datapoolId = datasetResponse?.data?.datapool?.id ?? null
  const { entities, isLoading, isError, getEntityById } = useDataSourceEntities({
    isEnabled: canReadDatasources && !isDatasetLoading && !isDatasetError,
    datapoolId,
  })

  const selectedEntity = data.entityId !== undefined ? getEntityById(data.entityId) : undefined

  const handleEntityChange = (entity: { id: string; name: string } | undefined) => {
    if (entity) {
      const fullEntity = getEntityById(entity.id)
      onUpdate({
        entityId: entity.id,
        entityName: entity.name,
        configured: true,
        entityMetadata: fullEntity?.connectorType ? { connector: fullEntity.connectorType } : undefined,
      })
    } else {
      onUpdate({
        entityId: undefined,
        entityName: undefined,
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
        placeholder={canReadDatasources ? t('dataSourcePanel.placeholder') : t('dataSourcePanel.noPermission')}
        entities={selectableEntities}
        selectedId={data.entityId}
        isLoading={isLoading || isDatasetLoading}
        isError={isError || isDatasetError}
        onChange={handleEntityChange}
        fallbackName={data.entityName}
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
