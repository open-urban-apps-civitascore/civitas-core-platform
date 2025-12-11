'use client'

import { FileDown, FileUp, Save } from 'lucide-react'
import { useCallback, useRef } from 'react'

import { Button } from '@/components/ui/button'

import { useActiveDiagram } from '../../hooks/useActiveDiagram'
import { downloadXmi } from '../../services/xmiExportService'
import { importXmiFromFile } from '../../services/xmiImportService'

interface ToolbarProps {
  onSave?: () => void
  hasUnsavedChanges?: boolean
  onExport?: () => void
}

export const Toolbar: React.FC<ToolbarProps> = ({ onSave, hasUnsavedChanges = false }) => {
  const { diagram, dispatch } = useActiveDiagram()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleSave = useCallback(() => {
    onSave?.()
  }, [onSave])

  const handleExportXmi = useCallback(() => {
    downloadXmi(diagram)
  }, [diagram])

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
        <Button
          variant="ghost"
          size="sm"
          onClick={handleSave}
          className={`h-8 px-2 ${hasUnsavedChanges ? 'text-blue-600' : ''}`}
          title={hasUnsavedChanges ? 'Save changes' : 'Save'}
        >
          <Save className="h-4 w-4" />
          <span className="ml-1 text-xs">Save</span>
        </Button>

        <Button variant="ghost" size="sm" onClick={handleImportClick} className="h-8 px-2" title="Import XMI file">
          <FileUp className="h-4 w-4" />
          <span className="ml-1 text-xs">Import</span>
        </Button>

        <Button variant="ghost" size="sm" onClick={handleExportXmi} className="h-8 px-2" title="Export diagram as XMI">
          <FileDown className="h-4 w-4" />
          <span className="ml-1 text-xs">Export</span>
        </Button>
      </div>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Status/Info Area */}
      <div className="text-xs text-gray-500">{/* Could show current zoom, selection count, etc. */}</div>
    </div>
  )
}
