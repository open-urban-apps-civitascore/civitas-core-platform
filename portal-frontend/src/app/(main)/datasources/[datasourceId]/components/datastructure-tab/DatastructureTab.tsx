import { RowSelectionState } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'

import { PageBackground } from '@/components/page-background/PageBackground'
import { UseMultiSessionReturn } from '@/components/uml-modeler/types/session'
import { UmlModeler } from '@/components/uml-modeler/UmlModeler'

import { DataModelImportModal } from './DataModelImportModal'

interface DatastructureTab {
  datasourceTitle: string
  selectedVersionId: string | null
  isReadOnly: boolean
  isInUse: boolean
  modelSessionManager: UseMultiSessionReturn
  onSelectDatastructureVersion: (selection: RowSelectionState) => void
}
export const DatastructureTab = (props: DatastructureTab) => {
  const { isReadOnly, isInUse, modelSessionManager, datasourceTitle, selectedVersionId, onSelectDatastructureVersion } =
    props
  const t = useTranslations('datastructureVersions')
  const [isImportDatastructureModalOpen, setIsImportDatastructureModalOpen] = useState(false)

  useEffect(() => {
    if (isInUse && !isReadOnly) toast.info(t('messages.isInUseModelHint'))
  }, [isReadOnly, isInUse, t])

  const handleImportFromDatastructure = () => {
    setIsImportDatastructureModalOpen(true)
  }

  const handleSelectVersion = (selection: RowSelectionState) => {
    onSelectDatastructureVersion(selection)
    setIsImportDatastructureModalOpen(false)
  }

  return isReadOnly || isInUse ? (
    <UmlModeler
      isReadOnly={isReadOnly || isInUse}
      modelSessionManager={modelSessionManager}
      isMultiSessionMode={false}
    />
  ) : (
    <PageBackground className="overflow-y-auto p-0" hasBackground={!isReadOnly && !isInUse}>
      <UmlModeler
        isReadOnly={isReadOnly || isInUse}
        modelSessionManager={modelSessionManager}
        isMultiSessionMode={false}
        canExportXmi={false}
        canImportXmi={false}
        onImportFromDatastructure={handleImportFromDatastructure}
      />
      <DataModelImportModal
        datasourceTitle={datasourceTitle}
        open={isImportDatastructureModalOpen}
        selectedVersion={selectedVersionId}
        onSelectVersion={handleSelectVersion}
        onOpenChange={() => setIsImportDatastructureModalOpen(false)}
      />
    </PageBackground>
  )
}
