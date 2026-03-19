import { RowSelectionState } from '@tanstack/react-table'
import Link from 'next/link'
import { useTranslations } from 'next-intl'
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
  const {
    modelSessionManager,
    datasourceTitle,
    selectedVersionId,
    isReadOnly,
    isDatasourceInUse,
    onSelectDatastructureVersion,
  } = props
  const t = useTranslations('datasources.dataModel.placeholder')
  const [isImportDatastructureModalOpen, setIsImportDatastructureModalOpen] = useState(false)

  const handleImportFromDatastructure = () => {
    setIsImportDatastructureModalOpen(true)
  }

  const handleSelectVersion = (selection: RowSelectionState) => {
    onSelectDatastructureVersion(selection)
    setIsImportDatastructureModalOpen(false)
  }

  const UmlCanvasPlaceholder = (
    <div className="text-center">
      <p className="text-xl text-foreground font-semibold mb-2">{t('title')}</p>
      <p className="text-muted-foreground text-sm mb-6">{t('description1')}</p>
      <p className="text-muted-foreground text-sm font-bold mb-2">{t('noDatastructures')}</p>
      <ol className="list-decimal list-inside text-center">
        <li className="text-muted-foreground text-sm">{t('creationSteps.step1')}</li>
        <li className="text-muted-foreground text-sm">
          {t.rich('creationSteps.step2', {
            link: chunks => (
              <Link className="underline" href="/datastructures">
                {chunks}
              </Link>
            ),
          })}
        </li>
        <li className="text-muted-foreground text-sm">{t('creationSteps.step3')}</li>
      </ol>
    </div>
  )

  return (
    <>
      <UmlModeler
        isReadOnly={true}
        modelSessionManager={modelSessionManager}
        isMultiSessionMode={false}
        canExportXmi={false}
        canImportXmi={false}
        placeHolder={UmlCanvasPlaceholder}
        onImportFromDatastructure={!isDatasourceInUse && !isReadOnly ? handleImportFromDatastructure : undefined}
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
