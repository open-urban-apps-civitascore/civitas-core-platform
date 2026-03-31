'use client'

import { FileDown, Loader2, Save } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useCallback, useRef } from 'react'
import { toast } from 'sonner'

import { useCreateModel } from '@/app/services/api/models/clientRequests'
import { BasicDropdownMenu } from '@/components/dropdown-menu/BasicDropdownMenu'
import { Button } from '@/components/ui/button'

import { useActiveDiagram } from '../../hooks/use-active-diagram'
import { buildUMLModelPayload } from '../../services/modelUploadService'
import { downloadXmi } from '../../services/xmiExportService'
import { importXmiFromFile } from '../../services/xmiImportService'

interface ToolbarProps {
  onSave?: () => void
  hasUnsavedChanges?: boolean
  sessionName?: string
  canExportXmi: boolean
  canImportXmi: boolean
  onImportFromDatastructure?: () => void
}

export const Toolbar: React.FC<ToolbarProps> = ({
  onSave,
  hasUnsavedChanges = false,
  sessionName,
  canExportXmi,
  canImportXmi,
  onImportFromDatastructure,
}) => {
  const { diagram, dispatch } = useActiveDiagram()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const createModel = useCreateModel()
  const isSaving = createModel.isPending
  const t = useTranslations('umlModeler')

  const handleSave = useCallback(() => {
    if (isSaving) return

    const payload = {
      ...buildUMLModelPayload(diagram),
      name: sessionName || diagram.name,
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

  const handleExportXmi = useCallback(() => {
    try {
      downloadXmi(diagram)
      toast.success(t('export.success'))
    } catch {
      toast.error(t('export.error'))
    }
  }, [diagram, t])

  const handleImportClick = useCallback(() => {
    fileInputRef.current?.click()
  }, [])

  const handleFileChange = useCallback(
    async (event: React.ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0]
      if (!file) return

      const result = await importXmiFromFile(file)

      if (result.success && result.diagram) {
        dispatch({ type: 'LOAD_DIAGRAM', payload: result.diagram })

        if (result.warnings.length > 0) {
          console.warn('Import warnings:', result.warnings)
        }
      } else {
        console.error('Import failed:', result.errors)
        alert(`Failed to import XMI file:\n${result.errors.join('\n')}`)
      }

      // Reset file input so the same file can be selected again
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    },
    [dispatch],
  )

  return (
    <div className="flex items-center gap-1 px-3 py-2 bg-white border-b border-gray-200">
      {/* Hidden file input for import */}
      <input
        ref={fileInputRef}
        type="file"
        accept=".xmi,.xml"
        onChange={handleFileChange}
        className="hidden"
        aria-hidden="true"
      />

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

        {(canImportXmi || onImportFromDatastructure) && (
          <BasicDropdownMenu
            title={t('import.title')}
            menuItems={[
              ...(canImportXmi
                ? [
                    {
                      label: t('import.fromFile'),
                      onClick: () => handleImportClick(),
                    },
                  ]
                : []),
              ...(onImportFromDatastructure
                ? [
                    {
                      label: t('import.fromPlatform'),
                      onClick: () => onImportFromDatastructure(),
                    },
                  ]
                : []),
            ]}
          />
        )}

        {canExportXmi && (
          <Button
            variant="ghost"
            size="sm"
            onClick={handleExportXmi}
            className="h-8 px-2"
            title="Export diagram as XMI"
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
