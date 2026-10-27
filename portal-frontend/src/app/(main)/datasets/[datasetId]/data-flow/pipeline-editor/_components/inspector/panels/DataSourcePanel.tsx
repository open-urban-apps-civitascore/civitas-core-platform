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
import { useState } from 'react'

import { StatusBadge } from '@/components/status-badge/StatusBadge'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { CONNECTOR_TYPES } from '@/const/connectors'
import { useDatasetPermissionsById } from '@/hooks/use-dataset-permissions'
import { cn } from '@/lib/utils'
import type { Datasource } from '@/types/datasources'

import { useDatastructureVersionInfo } from '../../../_hooks/use-datastructure-version-info'
import { usePipelineDatasources } from '../../../_hooks/use-pipeline-datasources'
import { apiCallNamesOf, apiCallsOf, type SelectableEntity } from '../../../_services/entityService'
import type { DataSourceNodeData } from '../../../_types/nodes'
import { DataSourceSelectModal } from '../components/DataSourceSelectModal'
import { EntityMetadata } from '../components/EntityMetadata'
import { EntitySelector } from '../components/EntitySelector'

interface DataSourcePanelProps {
  data: DataSourceNodeData
  onUpdate: (data: Partial<DataSourceNodeData>) => void
  isLocked: boolean
}

export const DataSourcePanel: React.FC<DataSourcePanelProps> = ({ data, onUpdate, isLocked }) => {
  const t = useTranslations('pipelineEditor')
  const tCommon = useTranslations('common')
  const { entities, isLoading, isError, canReadDatasources, getEntityById, getName } = usePipelineDatasources()
  const [isSelectModalOpen, setIsSelectModalOpen] = useState(false)

  const { datasetId } = useParams<{ datasetId: string }>()
  const { canReadDatastructures } = useDatasetPermissionsById(datasetId)

  const selectedEntity = data.entityId !== undefined ? getEntityById(data.entityId) : undefined
  const dataStructureVersion = selectedEntity?.dataStructureVersion ?? undefined
  const { name: dataStructureName } = useDatastructureVersionInfo(
    dataStructureVersion ? `${dataStructureVersion.dataStructureId}/${dataStructureVersion.id}` : undefined,
  )

  const handleSelect = (entity: Datasource | undefined) => {
    setIsSelectModalOpen(false)
    if (entity) {
      const apiCallNames = apiCallNamesOf(entity)
      onUpdate({
        entityId: entity.id,
        entityName: entity.name,
        // The CORE configurationUrn is emitted as the CORE pipeline node's `sourceRef`; round-trips via `styles`.
        configurationUrn: entity.configurationUrn ?? undefined,
        apiCallName: apiCallNames.length === 1 ? apiCallNames[0] : undefined,
        configured: true,
        entityMetadata: entity.connector ? { connector: entity.connector.connectorType } : undefined,
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

  const selectionText = () => {
    if (!canReadDatasources) return t('dataSourcePanel.noPermission')
    if (isLoading) return tCommon('loading')
    if (isError) return t('entitySelector.errorLoading')
    return getName(data.entityId) ?? t('dataSourcePanel.placeholder')
  }

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
      <div className="space-y-2">
        <Label htmlFor="dataSourceSelection">{t('dataSourcePanel.label')}</Label>
        <Button
          id="dataSourceSelection"
          variant="outline"
          size="sm"
          className={cn('w-full justify-start', isError && 'border-destructive text-destructive')}
          disabled={isLocked || !canReadDatasources || isLoading || isError}
          onClick={() => setIsSelectModalOpen(true)}
        >
          <span className="truncate">{selectionText()}</span>
        </Button>
      </div>

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
              { label: t('dataSourcePanel.status'), value: <StatusBadge status={selectedEntity.dataSourceStatus} /> },
              { label: t('dataSourcePanel.description'), value: selectedEntity.description ?? undefined },
              { label: t('dataSourcePanel.dataStructure'), value: dataStructureName },
              { label: t('dataSourcePanel.dataStructureVersion'), value: dataStructureVersion?.version ?? undefined },
              {
                label: t('dataSourcePanel.dataStructureVersionStatus'),
                value: dataStructureVersion ? (
                  <StatusBadge status={dataStructureVersion.dataStructureVersionStatus} />
                ) : undefined,
              },
            ]}
          />
          <div className="flex flex-wrap gap-4">
            <Button
              variant="outline"
              size="sm"
              onClick={() => window.open(`/datasources/${selectedEntity.id}`, '_blank')}
            >
              {t('dataSourcePanel.showDataSource')}
            </Button>
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
          </div>
        </>
      )}

      {canReadDatasources && (
        <DataSourceSelectModal
          isOpen={isSelectModalOpen}
          onOpenChange={setIsSelectModalOpen}
          datasources={entities}
          isLoading={isLoading}
          selectedId={data.entityId}
          onSelect={handleSelect}
        />
      )}
    </div>
  )
}
