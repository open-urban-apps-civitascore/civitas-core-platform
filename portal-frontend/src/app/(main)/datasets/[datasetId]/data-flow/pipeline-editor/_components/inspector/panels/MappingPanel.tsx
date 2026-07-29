'use client'

/**
 * MappingPanel Component
 *
 * Inspector panel for the Mapping node: name + source/target datastructure
 * selectors. Opens the fullscreen Schema-as-MegaNode editor when all are set.
 */

import { useParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useState } from 'react'

import { DataModelImportModal } from '@/app/(main)/datasources/[datasourceId]/components/datastructure-tab/DataModelImportModal'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

import type { StaTargetVocabulary } from '../../../_constants/staTargetCatalog'
import { parseCompositeKey, useDatastructureVersionInfo } from '../../../_hooks/use-datastructure-version-info'
import { usePipelinePermissions } from '../../../_hooks/use-pipeline-permissions'
import type { MappingNodeData } from '../../../_types/nodes'
import { emptyMappingConfig, type MappingConfig } from '../../mapping-editor/_types'
import { MappingEditorModal } from '../../mapping-editor/MappingEditorModal'

interface SchemaSelection {
  datastructureId: string
  versionId: string
}

interface DatastructureFieldProps {
  label: string
  placeholder: string
  selectedKey: string | null
  name?: string
  onSelect: (selection: SchemaSelection) => void
}

/** Datastructure-version picker reusing DataModelImportModal. */
const DatastructureField = ({ label, placeholder, selectedKey, name, onSelect }: DatastructureFieldProps) => {
  const { datasetId } = useParams<{ datasetId: string }>()
  const { canReadDatastructures } = usePipelinePermissions(datasetId)
  const [isOpen, setIsOpen] = useState(false)

  const handleSelect = (selection: Record<string, boolean>) => {
    const key = Object.keys(selection).find(k => selection[k])
    const parsed = key ? parseCompositeKey(key) : null
    if (parsed) onSelect(parsed)
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
          canRemoveSelection={false}
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

  // Names are not stored on the node — resolve them from the version references at render time.
  const { name: sourceName } = useDatastructureVersionInfo(sourceKey ?? undefined)
  const { name: targetName } = useDatastructureVersionInfo(targetKey ?? undefined)

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
      ...invalidateMapping,
    })

  const handleTarget = (sel: SchemaSelection) =>
    onUpdate({
      targetDatastructureId: sel.datastructureId,
      targetVersionId: sel.versionId,
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
        name={sourceName}
        onSelect={handleSource}
      />
      <DatastructureField
        label={t('outputDatastructure')}
        placeholder={t('selectDatastructure')}
        selectedKey={targetKey}
        name={targetName}
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
            name: sourceName,
          }}
          target={{
            datastructureId: data.targetDatastructureId!,
            versionId: data.targetVersionId!,
            name: targetName,
          }}
          config={data.mappingConfig}
          onSave={handleSave}
        />
      )}
    </div>
  )
}
