'use client'

/**
 * MappingPanel Component
 *
 * Inspector panel for the Mapping node: name + source/target datastructure
 * selectors. Opens the fullscreen Schema-as-MegaNode editor when all are set.
 */

import { useParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { DataModelImportModal } from '@/app/(main)/datasources/[datasourceId]/components/datastructure-tab/DataModelImportModal'
import { useGetDatastructureVersion } from '@/app/services/api/datastructures/versions/clientRequests'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

import type { StaTargetVocabulary } from '../../../_constants/staTargetCatalog'
import { usePipelinePermissions } from '../../../_hooks/use-pipeline-permissions'
import type { MappingNodeData } from '../../../_types/nodes'
import { emptyMappingConfig, type MappingConfig } from '../../mapping-editor/_types'
import { MappingEditorModal } from '../../mapping-editor/MappingEditorModal'

const parseCompositeKey = (key: string): { datastructureId: string; versionId: string } | null => {
  const [datastructureId, versionId, ...rest] = key.split('/')
  if (!datastructureId || !versionId || rest.length > 0) return null
  return { datastructureId, versionId }
}

interface SchemaSelection {
  datastructureId: string
  versionId: string
  name: string
}

interface DatastructureFieldProps {
  label: string
  placeholder: string
  selectedKey: string | null
  name?: string
  onSelect: (selection: SchemaSelection) => void
}

/** Datastructure-version picker reusing DataModelImportModal, resolving the schema name. */
const DatastructureField = ({ label, placeholder, selectedKey, name, onSelect }: DatastructureFieldProps) => {
  const { datasetId } = useParams<{ datasetId: string }>()
  const { canReadDatastructures } = usePipelinePermissions(datasetId)

  const [isOpen, setIsOpen] = useState(false)
  const [pendingKey, setPendingKey] = useState<string | null>(null)
  const parsed = useMemo(() => (pendingKey ? parseCompositeKey(pendingKey) : null), [pendingKey])

  const { data: versionResponse } = useGetDatastructureVersion({
    datastructureId: parsed?.datastructureId ?? '',
    versionId: parsed?.versionId ?? '',
    isEnabled: !!parsed,
  })

  useEffect(() => {
    if (!versionResponse?.data || !pendingKey) return
    const p = parseCompositeKey(pendingKey)
    if (p) onSelect({ ...p, name: versionResponse.data.dataStructure?.name ?? '' })
    setPendingKey(null)
  }, [versionResponse?.data, pendingKey]) // eslint-disable-line react-hooks/exhaustive-deps

  const handleSelect = (selection: Record<string, boolean>) => {
    const key = Object.keys(selection).find(k => selection[k])
    if (key && parseCompositeKey(key)) setPendingKey(key)
    setIsOpen(false)
  }

  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      <Button variant="outline" size="sm" className="w-full justify-start" onClick={() => setIsOpen(true)}>
        <span className="truncate">{name || placeholder}</span>
      </Button>
      {canReadDatastructures && (
        <DataModelImportModal
          open={isOpen}
          onOpenChange={setIsOpen}
          selectedVersion={selectedKey}
          datasourceTitle={name || label}
          onSelectVersion={handleSelect}
        />
      )}
    </div>
  )
}

interface MappingPanelProps {
  data: MappingNodeData
  onUpdate: (data: Partial<MappingNodeData>) => void
}

export const MappingPanel = ({ data, onUpdate }: MappingPanelProps) => {
  const t = useTranslations('pipelineEditor.mappingPanel')
  const [isEditorOpen, setIsEditorOpen] = useState(false)

  const sourceKey =
    data.sourceDatastructureId && data.sourceVersionId ? `${data.sourceDatastructureId}/${data.sourceVersionId}` : null
  const targetKey =
    data.targetDatastructureId && data.targetVersionId ? `${data.targetDatastructureId}/${data.targetVersionId}` : null
  const canOpen = Boolean(data.label.trim() && sourceKey && targetKey)

  // The node is "configured" (deployable) ONLY after the field mapping has been saved (handleSave).
  // The name and the source/target selection alone never mark it configured — otherwise a node with
  // source+target but no actual mapping would pass validation and deploy an empty transformation.
  const handleName = (e: React.ChangeEvent<HTMLInputElement>) => onUpdate({ label: e.target.value })

  // Changing the source or target invalidates any previously-saved mapping (it was built against the
  // old schema), so reset the saved mapping + its required-field snapshot and un-configure the node;
  // the user must re-open the editor and save again.
  const invalidateMapping = {
    mappingConfig: emptyMappingConfig(),
    targetRequiredFields: undefined,
    staMatchKeys: undefined,
    configured: false,
  }

  const handleSource = (sel: SchemaSelection) =>
    onUpdate({
      sourceDatastructureId: sel.datastructureId,
      sourceVersionId: sel.versionId,
      sourceName: sel.name,
      ...invalidateMapping,
    })

  const handleTarget = (sel: SchemaSelection) =>
    onUpdate({
      targetDatastructureId: sel.datastructureId,
      targetVersionId: sel.versionId,
      targetName: sel.name,
      ...invalidateMapping,
    })

  const handleSave = (config: MappingConfig, targetRequiredFields: string[], staMatchKeys: StaTargetVocabulary) =>
    onUpdate({ mappingConfig: config, targetRequiredFields, staMatchKeys, configured: true })

  return (
    <div className="space-y-4 p-4">
      <div className="space-y-2">
        <Label htmlFor="mappingName">{t('name')}</Label>
        <Input id="mappingName" value={data.label} onChange={handleName} placeholder={t('namePlaceholder')} />
      </div>

      <DatastructureField
        label={t('inputDatastructure')}
        placeholder={t('selectDatastructure')}
        selectedKey={sourceKey}
        name={data.sourceName}
        onSelect={handleSource}
      />
      <DatastructureField
        label={t('outputDatastructure')}
        placeholder={t('selectDatastructure')}
        selectedKey={targetKey}
        name={data.targetName}
        onSelect={handleTarget}
      />

      <Button className="w-full" disabled={!canOpen} onClick={() => setIsEditorOpen(true)}>
        {t('openMappingEditor')}
      </Button>

      {canOpen && (
        <MappingEditorModal
          open={isEditorOpen}
          onOpenChange={setIsEditorOpen}
          name={data.label}
          source={{
            datastructureId: data.sourceDatastructureId!,
            versionId: data.sourceVersionId!,
            name: data.sourceName,
          }}
          target={{
            datastructureId: data.targetDatastructureId!,
            versionId: data.targetVersionId!,
            name: data.targetName,
          }}
          config={data.mappingConfig}
          onSave={handleSave}
        />
      )}
    </div>
  )
}
