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

import { Button } from '@/components/ui/button'
import { CONNECTOR_TYPES } from '@/const/connectors'
import { useDatasetPermissionsById } from '@/hooks/use-dataset-permissions'

import { useDatastructureVersionInfo } from '../../../_hooks/use-datastructure-version-info'
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
  isLocked: boolean
}

export const DataSourcePanel: React.FC<DataSourcePanelProps> = ({ data, onUpdate, isLocked }) => {
  const t = useTranslations('pipelineEditor')
  const tStatus = useTranslations('common.status')
  const { entities, isLoading, isError, canReadDatasources, getEntityById, getName } = usePipelineDatasources()

  const { datasetId } = useParams<{ datasetId: string }>()
  const { canReadDatastructures } = useDatasetPermissionsById(datasetId)

  const selectedEntity = data.entityId !== undefined ? getEntityById(data.entityId) : undefined
  const dataStructureVersion = selectedEntity?.dataStructureVersion ?? undefined
  const { name: dataStructureName } = useDatastructureVersionInfo(
    dataStructureVersion ? `${dataStructureVersion.dataStructureId}/${dataStructureVersion.id}` : undefined,
  )

  const handleEntityChange = (entity: { id: string; name: string } | undefined) => {
    if (entity) {
      const fullEntity = getEntityById(entity.id)
      const apiCallNames = fullEntity ? apiCallNamesOf(fullEntity) : []
      onUpdate({
        entityId: entity.id,
        entityName: entity.name,
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
  const isApiCallNotFound = data.apiCallName !== undefined && !methodByApiCallName.has(data.apiCallName)

  const renderApiCallName = (apiCall: SelectableEntity) => {
    if (!methodByApiCallName.has(apiCall.name)) {
      return <span className="text-muted-foreground">{apiCall.name}</span>
    }
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
        isDisabled={isLocked}
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
          errorMessage={isApiCallNotFound ? t('dataSourcePanel.apiCallNotFound') : undefined}
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
              { label: t('dataSourcePanel.dataStructure'), value: dataStructureName },
              { label: t('dataSourcePanel.dataStructureVersion'), value: dataStructureVersion?.version ?? undefined },
              {
                label: t('dataSourcePanel.dataStructureVersionStatus'),
                value: dataStructureVersion ? tStatus(dataStructureVersion.dataStructureVersionStatus) : undefined,
              },
            ]}
          />
          {canReadDatastructures && dataStructureVersion && (
            <Button
              variant="outline"
              size="sm"
              onClick={() =>
                window.open(
                  `/datastructures/${dataStructureVersion.dataStructureId}/${dataStructureVersion.id}`,
                  '_blank',
                )
              }
            >
              {t('dataSourcePanel.showDataStructure')}
            </Button>
          )}
        </>
      )}
    </div>
  )
}
