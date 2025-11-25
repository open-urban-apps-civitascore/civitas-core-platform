'use client'

import { FileDown, FileUp, Save } from 'lucide-react'
import { useCallback } from 'react'

import { Button } from '@/components/ui/button'

interface ToolbarProps {
  onSave?: () => void
  onExport?: () => void
  hasUnsavedChanges?: boolean
}

export const Toolbar: React.FC<ToolbarProps> = ({ onSave, onExport, hasUnsavedChanges = false }) => {
  const handleSave = useCallback(() => {
    onSave?.()
  }, [onSave])

  const handleExportData = useCallback(() => {
    onExport?.()
  }, [onExport])

  return (
    <div className="flex items-center gap-1 px-3 py-2 bg-white border-b border-gray-200">
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

        <Button variant="ghost" size="sm" onClick={handleExportData} className="h-8 px-2" title="Export diagram data">
          <FileUp className="h-4 w-4" />
          <span className="ml-1 text-xs">Imprt</span>
        </Button>
        <Button variant="ghost" size="sm" onClick={handleExportData} className="h-8 px-2" title="Export diagram data">
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
