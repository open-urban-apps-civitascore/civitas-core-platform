'use client'

/**
 * GeoPersistencePanel Component
 *
 * Inspector panel for Geo Persistence storage nodes.
 * Allows configuring a table name and importing a data structure version.
 *
 */

import { useParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useState } from 'react'

import {
  DataModelImportModal,
  type SelectedDatastructureVersion,
} from '@/app/(main)/datasources/[datasourceId]/components/datastructure-tab/DataModelImportModal'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useDatasetPermissionsById } from '@/hooks/use-dataset-permissions'
import { buildDataStructureUrn } from '@/utils/urn'

import { useActivePipeline } from '../../../_hooks/use-active-pipeline'
import { parseCompositeKey, useDatastructureVersionInfo } from '../../../_hooks/use-datastructure-version-info'
import { isValidTableName, MAX_TABLE_NAME_LENGTH } from '../../../_services/dataSinkNameService'
import type { GeoPersistenceNodeData } from '../../../_types/nodes'
import { EntityMetadata } from '../components/EntityMetadata'

interface GeoPersistencePanelProps {
  data: GeoPersistenceNodeData
  onUpdate: (data: Partial<GeoPersistenceNodeData>) => void
}

export const GeoPersistencePanel: React.FC<GeoPersistencePanelProps> = ({ data, onUpdate }) => {
  const t = useTranslations('pipelineEditor')
  const { datasetId } = useParams<{ datasetId: string }>()
  const { canReadDatastructures } = useDatasetPermissionsById(datasetId)
  const { selectedNode, pipelineUsingTableName, getSinkLocks } = useActivePipeline()
  const [isImportModalOpen, setIsImportModalOpen] = useState(false)

  const { provisioned: isProvisioned, inUseByLayer: isUsedByLayer } = getSinkLocks(data.entityId)

  const conflictingPipeline = selectedNode ? pipelineUsingTableName(selectedNode.id, data.tableName) : null
  const hasInvalidCharacters = data.tableName !== '' && !isValidTableName(data.tableName)

  const { name: dataStructureName, versionNumber } = useDatastructureVersionInfo(data.dataStructureVersionId)

  const handleTableNameChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const tableName = e.target.value
      onUpdate({
        tableName,
        configured: isValidTableName(tableName) && !!data.dataStructureVersionId,
      })
    },
    [data.dataStructureVersionId, onUpdate],
  )

  const handleSelectVersion = useCallback(
    (selection: Record<string, boolean>, selectedVersion?: SelectedDatastructureVersion) => {
      const selectedKey = Object.keys(selection).find(key => selection[key])
      if (selectedKey && parseCompositeKey(selectedKey) && selectedVersion) {
        const { datastructureId, name, version } = selectedVersion
        onUpdate({
          dataStructureVersionId: selectedKey,
          dataStructureUrn: buildDataStructureUrn(name, datastructureId, version),
          configured: isValidTableName(data.tableName),
        })
      }
      setIsImportModalOpen(false)
    },
    [data.tableName, onUpdate],
  )

  return (
    <div className="space-y-4 p-4">
      <div className="space-y-2">
        <Label htmlFor="tableName">{t('geoPersistencePanel.tableName')}</Label>
        <Input
          id="tableName"
          value={data.tableName}
          onChange={handleTableNameChange}
          placeholder={t('geoPersistencePanel.tableNamePlaceholder')}
          maxLength={MAX_TABLE_NAME_LENGTH}
          aria-invalid={hasInvalidCharacters || conflictingPipeline !== null}
        />
        {hasInvalidCharacters && (
          <p className="text-xs text-destructive">{t('geoPersistencePanel.tableNameInvalid')}</p>
        )}
        {!hasInvalidCharacters && conflictingPipeline !== null && (
          <p className="text-xs text-destructive">
            {t('validation.messages.duplicateTableName', {
              tableName: data.tableName.trim(),
              pipeline: conflictingPipeline,
            })}
          </p>
        )}
      </div>

      {!isProvisioned && (
        <div className="space-y-2">
          <Label>{t('geoPersistencePanel.dataStructureVersion')}</Label>
          <Button
            variant="outline"
            size="sm"
            className="w-full"
            disabled={isUsedByLayer}
            onClick={() => setIsImportModalOpen(true)}
          >
            {data.dataStructureVersionId
              ? t('geoPersistencePanel.changeDataStructure')
              : t('geoPersistencePanel.importDataStructure')}
          </Button>
        </div>
      )}

      {data.dataStructureVersionId && (
        <>
          <EntityMetadata
            title={t('geoPersistencePanel.details')}
            items={[
              { label: t('geoPersistencePanel.dataStructureName'), value: dataStructureName },
              { label: t('geoPersistencePanel.versionNumber'), value: versionNumber },
            ]}
          />
          {canReadDatastructures && parseCompositeKey(data.dataStructureVersionId) && (
            <button
              onClick={() =>
                window.open(
                  `/datastructures/${parseCompositeKey(data.dataStructureVersionId!)!.datastructureId}`,
                  '_blank',
                )
              }
              className="w-full rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 cursor-pointer focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
            >
              {t('dataSourcePanel.showDataStructure')}
            </button>
          )}
        </>
      )}

      {canReadDatastructures && (
        <DataModelImportModal
          open={isImportModalOpen}
          onOpenChange={setIsImportModalOpen}
          selectedVersion={data.dataStructureVersionId ?? null}
          datasourceTitle={data.tableName || t('geoPersistencePanel.title')}
          onSelectVersion={handleSelectVersion}
        />
      )}
    </div>
  )
}
