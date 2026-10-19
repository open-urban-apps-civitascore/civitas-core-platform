'use client'

/**
 * DataSourcePanel Component
 *
 * Inspector panel for DataSource nodes.
 * Allows selecting a datasource entity and displays its metadata.
 *
 */
import { useTranslations } from 'next-intl'

import { CONNECTOR_TYPES } from '@/const/connectors'

import { usePipelineDatasources } from '../../../_hooks/use-pipeline-datasources'
import {
  apiCallNamesOf,
  apiCallsOf,
  datasourceToSelectable,
  type SelectableEntity,
} from '../../../_services/entityService'
import type { DataSourceNodeData } from '../../../_types/nodes'
import { EntityMetadata } from '../components/EntityMetadata'
import { EntitySelector } from '../components/EntitySelector'
interface DataSourcePanelProps {
  data: DataSourceNodeData
  onUpdate: (data: Partial<DataSourceNodeData>) => void
}

export const DataSourcePanel: React.FC<DataSourcePanelProps> = ({ data, onUpdate }) => {
  const t = useTranslations('pipelineEditor')
  const tStatus = useTranslations('common.status')
  const { entities, isLoading, isError, canReadDatasources, getEntityById, getName } = usePipelineDatasources()

  const selectedEntity = data.entityId !== undefined ? getEntityById(data.entityId) : undefined

  const handleEntityChange = (entity: { id: string; name: string } | undefined) => {
    if (entity) {
      const fullEntity = getEntityById(entity.id)
      const apiCallNames = fullEntity ? apiCallNamesOf(fullEntity) : []
      onUpdate({
        entityId: entity.id,
        entityName: entity.name,
        // Resolve the CORE configurationUrn from the picker's fetched list so it can be emitted as
        // the CORE pipeline node's `sourceRef`; round-trips via `styles`.
        configurationUrn: fullEntity?.configurationUrn ?? undefined,
        apiCallName: apiCallNames.length === 1 ? apiCallNames[0] : undefined,
        configured: true,
        entityMetadata: fullEntity?.connector ? { connector: fullEntity.connector.connectorType } : undefined,
      })
    } else {
      onUpdate({
        entityId: undefined,
        entityName: undefined,
        configurationUrn: undefined,
        apiCallName: undefined,
        entityMetadata: undefined,
        configured: false,
      })
    }
  }

  const selectableEntities = entities.map(datasourceToSelectable)

  const isHttpSource = selectedEntity?.connector?.connectorType === CONNECTOR_TYPES.HTTP
  const apiCalls = selectedEntity ? apiCallsOf(selectedEntity) : []
  const selectableApiCalls = apiCalls.map(apiCall => ({ id: apiCall.name, name: apiCall.name }))
  const methodByApiCallName = new Map(apiCalls.map(apiCall => [apiCall.name, apiCall.method]))

  const renderApiCallName = (apiCall: SelectableEntity) => {
    const method = methodByApiCallName.get(apiCall.name)
    return (
      <>
        {method && <span className="text-sm font-semibold text-primary">{method}</span>} {apiCall.name}
      </>
    )
  }

  return (
    <div className="space-y-4 p-4">
      <EntitySelector
        label={t('dataSourcePanel.label')}
        placeholder={canReadDatasources ? t('dataSourcePanel.placeholder') : t('dataSourcePanel.noPermission')}
        entities={selectableEntities}
        selectedId={data.entityId}
        isLoading={isLoading}
        isError={isError}
        onChange={handleEntityChange}
        fallbackName={getName(data.entityId)}
      />

      {isHttpSource && (
        <EntitySelector
          label={t('dataSourcePanel.apiCall')}
          placeholder={
            selectableApiCalls.length > 0 ? t('dataSourcePanel.apiCallPlaceholder') : t('dataSourcePanel.noApiCalls')
          }
          entities={selectableApiCalls}
          selectedId={data.apiCallName}
          isLoading={isLoading}
          isError={isError}
          onChange={apiCall => onUpdate({ apiCallName: apiCall?.name })}
          fallbackName={data.apiCallName}
          renderEntityName={renderApiCallName}
        />
      )}

      {selectedEntity && (
        <>
          <EntityMetadata
            title={t('dataSourcePanel.details')}
            items={[
              { label: t('dataSourcePanel.connector'), value: selectedEntity.connector?.connectorType },
              { label: t('dataSourcePanel.status'), value: tStatus(selectedEntity.dataSourceStatus) },
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
