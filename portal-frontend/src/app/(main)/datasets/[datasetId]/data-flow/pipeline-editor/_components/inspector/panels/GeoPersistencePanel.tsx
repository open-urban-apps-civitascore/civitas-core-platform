'use client'

/**
 * GeoPersistencePanel Component
 *
 * Inspector panel for Geo Persistence storage nodes.
 * Allows configuring a table name and importing a data structure version.
 *
 */

import { useTranslations } from 'next-intl'
import { useCallback, useState } from 'react'

import { DataModelImportModal } from '@/app/(main)/datasources/[datasourceId]/components/datastructure-tab/DataModelImportModal'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

import type { GeoPersistenceNodeData } from '../../../_types/nodes'
import { EntityMetadata } from '../components/EntityMetadata'

interface GeoPersistencePanelProps {
  data: GeoPersistenceNodeData
  onUpdate: (data: Partial<GeoPersistenceNodeData>) => void
}

export const GeoPersistencePanel: React.FC<GeoPersistencePanelProps> = ({ data, onUpdate }) => {
  const t = useTranslations('pipelineEditor')
  const [isImportModalOpen, setIsImportModalOpen] = useState(false)

  const handleTableNameChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      // Only allow letters, numbers, and underscores
      const tableName = e.target.value.replace(/[^a-zA-Z0-9_]/g, '')
      const hasDataStructure = !!data.dataStructureVersionId
      onUpdate({
        tableName,
        configured: tableName.trim().length > 0 && hasDataStructure,
      })
    },
    [data.dataStructureVersionId, onUpdate],
  )

  const handleSelectVersion = useCallback(
    (selection: Record<string, boolean>) => {
      // Selection format from DataModelImportModal: "datastructureId/versionId"
      const selectedKey = Object.keys(selection).find(key => selection[key])
      if (selectedKey) {
        const parts = selectedKey.split('/')
        if (parts.length === 2) {
          const [dataStructureName, versionId] = parts
          onUpdate({
            dataStructureVersionId: versionId,
            dataStructureName: dataStructureName,
            versionNumber: versionId,
            configured: data.tableName.trim().length > 0,
          })
        }
      }
      setIsImportModalOpen(false)
    },
    [data.tableName, onUpdate],
  )

  return (
    <div className="space-y-4 p-4">
      {/* Table Name */}
      <div className="space-y-2">
        <Label htmlFor="tableName">{t('geoPersistencePanel.tableName')}</Label>
        <Input
          id="tableName"
          value={data.tableName}
          onChange={handleTableNameChange}
          placeholder={t('geoPersistencePanel.tableNamePlaceholder')}
        />
      </div>

      {/* Data Structure Version */}
      <div className="space-y-2">
        <Label>{t('geoPersistencePanel.dataStructureVersion')}</Label>
        <Button variant="outline" size="sm" className="w-full" onClick={() => setIsImportModalOpen(true)}>
          {data.dataStructureVersionId
            ? t('geoPersistencePanel.changeDataStructure')
            : t('geoPersistencePanel.importDataStructure')}
        </Button>
      </div>

      {/* Selected Data Structure Details */}
      {data.dataStructureVersionId && (
        <EntityMetadata
          title={t('geoPersistencePanel.details')}
          items={[
            { label: t('geoPersistencePanel.dataStructureName'), value: data.dataStructureName },
            { label: t('geoPersistencePanel.versionNumber'), value: data.versionNumber },
          ]}
        />
      )}

      {/* Import Modal */}
      <DataModelImportModal
        open={isImportModalOpen}
        onOpenChange={setIsImportModalOpen}
        selectedVersion={data.dataStructureVersionId ?? null}
        datasourceTitle={data.tableName || t('geoPersistencePanel.title')}
        onSelectVersion={handleSelectVersion}
      />
    </div>
  )
}
