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
import { useCallback, useEffect, useMemo, useState } from 'react'

import { DataModelImportModal } from '@/app/(main)/datasources/[datasourceId]/components/datastructure-tab/DataModelImportModal'
import { useGetDatastructureVersion } from '@/app/services/api/datastructures/versions/clientRequests'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

import { usePipelinePermissions } from '../../../_hooks/use-pipeline-permissions'
import type { GeoPersistenceNodeData } from '../../../_types/nodes'
import { EntityMetadata } from '../components/EntityMetadata'

/**
 * Parses the composite selection key from DataModelImportModal.
 * The key format is "datastructureId/versionId".
 */
const parseCompositeKey = (key: string): { datastructureId: string; versionId: string } | null => {
  const parts = key.split('/')
  if (parts.length !== 2) return null
  return { datastructureId: parts[0], versionId: parts[1] }
}

interface GeoPersistencePanelProps {
  data: GeoPersistenceNodeData
  onUpdate: (data: Partial<GeoPersistenceNodeData>) => void
}

export const GeoPersistencePanel: React.FC<GeoPersistencePanelProps> = ({ data, onUpdate }) => {
  const t = useTranslations('pipelineEditor')
  const { datasetId } = useParams<{ datasetId: string }>()
  const { canReadDatastructures } = usePipelinePermissions(datasetId)
  const [isImportModalOpen, setIsImportModalOpen] = useState(false)
  const [pendingKey, setPendingKey] = useState<string | null>(null)

  const parsed = useMemo(() => (pendingKey ? parseCompositeKey(pendingKey) : null), [pendingKey])

  const { data: versionResponse } = useGetDatastructureVersion({
    datastructureId: parsed?.datastructureId ?? '',
    versionId: parsed?.versionId ?? '',
    isEnabled: !!parsed,
  })

  useEffect(() => {
    if (!versionResponse?.data || !pendingKey) return

    const { dataStructure, version } = versionResponse.data
    onUpdate({
      dataStructureVersionId: pendingKey,
      dataStructureName: dataStructure?.name ?? '',
      versionNumber: version ?? '',
      configured: data.tableName.trim().length > 0,
    })
    setPendingKey(null)
  }, [versionResponse?.data, pendingKey]) // eslint-disable-line react-hooks/exhaustive-deps

  const handleTableNameChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const tableName = e.target.value.replace(/[^a-zA-Z0-9_]/g, '')
      onUpdate({
        tableName,
        configured: tableName.trim().length > 0 && !!data.dataStructureVersionId,
      })
    },
    [data.dataStructureVersionId, onUpdate],
  )

  const handleSelectVersion = useCallback((selection: Record<string, boolean>) => {
    const selectedKey = Object.keys(selection).find(key => selection[key])
    if (selectedKey && parseCompositeKey(selectedKey)) {
      setPendingKey(selectedKey)
    }
    setIsImportModalOpen(false)
  }, [])

  return (
    <div className="space-y-4 p-4">
      <div className="space-y-2">
        <Label htmlFor="tableName">{t('geoPersistencePanel.tableName')}</Label>
        <Input
          id="tableName"
          value={data.tableName}
          onChange={handleTableNameChange}
          placeholder={t('geoPersistencePanel.tableNamePlaceholder')}
        />
      </div>

      <div className="space-y-2">
        <Label>{t('geoPersistencePanel.dataStructureVersion')}</Label>
        <Button variant="outline" size="sm" className="w-full" onClick={() => setIsImportModalOpen(true)}>
          {data.dataStructureVersionId
            ? t('geoPersistencePanel.changeDataStructure')
            : t('geoPersistencePanel.importDataStructure')}
        </Button>
      </div>

      {data.dataStructureVersionId && (
        <>
          <EntityMetadata
            title={t('geoPersistencePanel.details')}
            items={[
              { label: t('geoPersistencePanel.dataStructureName'), value: data.dataStructureName },
              { label: t('geoPersistencePanel.versionNumber'), value: data.versionNumber },
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
