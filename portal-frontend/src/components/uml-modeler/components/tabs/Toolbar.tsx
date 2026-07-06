'use client'

import { FileDown, Loader2, Save } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useCallback } from 'react'
import { toast } from 'sonner'

import { useCreateModel } from '@/app/services/api/models/clientRequests'
import { BasicDropdownMenu } from '@/components/dropdown-menu/BasicDropdownMenu'
import { Button } from '@/components/ui/button'

import { useActiveDiagram } from '../../hooks/use-active-diagram'
import { downloadJsonSchema, UnsupportedRelationshipError } from '../../services/jsonSchemaExportService'
import { buildUMLModelPayload } from '../../services/modelUploadService'

interface ToolbarProps {
  onSave?: () => void
  hasUnsavedChanges?: boolean
  sessionName?: string
  canExportModel: boolean
  onImportFromDatastructure?: () => void
}

export const Toolbar: React.FC<ToolbarProps> = ({
  onSave,
  hasUnsavedChanges = false,
  sessionName,
  canExportModel,
  onImportFromDatastructure,
}) => {
  const { diagram } = useActiveDiagram()
  const createModel = useCreateModel()
  const isSaving = createModel.isPending
  const t = useTranslations('umlModeler')

  const handleSave = useCallback(() => {
    if (isSaving) return

    let payload
    try {
      payload = {
        ...buildUMLModelPayload(diagram),
        name: sessionName || diagram.name,
      }
    } catch (error) {
      toast.error(error instanceof UnsupportedRelationshipError ? t('save.unsupportedRelationships') : t('save.error'))
      return
    }

    createModel.mutate(payload, {
      onSuccess: () => {
        onSave?.()
        toast.success(t('save.success'))
      },
      onError: () => {
        toast.error(t('save.error'))
      },
    })
  }, [diagram, isSaving, onSave, createModel, sessionName, t])

  const handleExport = useCallback(() => {
    try {
      downloadJsonSchema(diagram)
      toast.success(t('export.success'))
    } catch (error) {
      toast.error(
        error instanceof UnsupportedRelationshipError ? t('export.unsupportedRelationships') : t('export.error'),
      )
    }
  }, [diagram, t])

  return (
    <div className="flex items-center gap-1 px-3 py-2 bg-white border-b border-gray-200">
      {/* File Operations */}
      <div className="flex items-center gap-1 mr-3">
        {onSave && (
          <Button
            variant="ghost"
            size="sm"
            onClick={handleSave}
            disabled={isSaving}
            className={`h-8 px-2 ${hasUnsavedChanges ? 'text-blue-600' : ''}`}
            title={isSaving ? 'Saving...' : hasUnsavedChanges ? 'Save changes' : 'Save'}
          >
            {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            <span className="ml-1 text-xs">{isSaving ? 'Saving...' : 'Save'}</span>
          </Button>
        )}

        {onImportFromDatastructure && (
          <BasicDropdownMenu
            title={t('import.title')}
            menuItems={[
              {
                label: t('import.fromPlatform'),
                onClick: () => onImportFromDatastructure(),
              },
            ]}
          />
        )}

        {canExportModel && (
          <Button
            variant="ghost"
            size="sm"
            onClick={handleExport}
            className="h-8 px-2"
            title="Export diagram as JSON Schema"
          >
            <FileDown className="h-4 w-4" />
            <span className="ml-1 text-xs">Export</span>
          </Button>
        )}
      </div>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Status/Info Area */}
      <div className="text-xs text-gray-500">{/* Could show current zoom, selection count, etc. */}</div>
    </div>
  )
}
