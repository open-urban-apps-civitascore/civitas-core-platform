import { RowSelectionState } from '@tanstack/react-table'
import { useState } from 'react'

import { UseMultiSessionReturn } from '@/components/uml-modeler/types/session'
import { UmlModeler } from '@/components/uml-modeler/UmlModeler'

import { DataModelImportModal } from './DataModelImportModal'

interface DatastructureTab {
  datasourceTitle: string
  selectedVersionId: string | null
  isReadOnly: boolean
  isDatasourceInUse: boolean
  modelSessionManager: UseMultiSessionReturn
  onSelectDatastructureVersion: (selection: RowSelectionState) => void
}
export const DatastructureTab = (props: DatastructureTab) => {
  const { modelSessionManager, datasourceTitle, selectedVersionId, isDatasourceInUse, onSelectDatastructureVersion } = props
  const [isImportDatastructureModalOpen, setIsImportDatastructureModalOpen] = useState(false)

  const handleImportFromDatastructure = () => {
    setIsImportDatastructureModalOpen(true)
  }

  const handleSelectVersion = (selection: RowSelectionState) => {
    onSelectDatastructureVersion(selection)
    setIsImportDatastructureModalOpen(false)
  }

  return (
    <>
      <UmlModeler
        isReadOnly={true}
        modelSessionManager={modelSessionManager}
        isMultiSessionMode={false}
        canExportXmi={false}
        canImportXmi={false}
        onImportFromDatastructure={isDatasourceInUse ? undefined : handleImportFromDatastructure}
      />
      <DataModelImportModal
        datasourceTitle={datasourceTitle}
        open={isImportDatastructureModalOpen}
        selectedVersion={selectedVersionId}
        onSelectVersion={handleSelectVersion}
        onOpenChange={() => setIsImportDatastructureModalOpen(false)}
      />
    </>
  )
}
