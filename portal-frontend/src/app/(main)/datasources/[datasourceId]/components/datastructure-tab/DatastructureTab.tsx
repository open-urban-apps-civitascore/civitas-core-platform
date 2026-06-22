import { RowSelectionState } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import {
  defaultDatastructureVersionFormData,
  useDatastructureVersion,
} from '@/app/(main)/datastructures/[datastructureId]/(versions)/hooks/useDatastructureVersion'
import { useGetDatastructureVersion } from '@/app/services/api/datastructures/versions/clientRequests'
import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { UmlModeler } from '@/components/uml-modeler/UmlModeler'
import { DatastructureVersion } from '@/types/datastructures'
import { mapDatastructureVersionApiToFormData } from '@/utils/datastructures'

import { DataModelImportModal } from './DataModelImportModal'

interface DatastructureTabProps {
  datasourceTitle: string
  selectedVersionId: string | null
  isReadOnly: boolean
  isDatasourceInUse: boolean
  onSelectDatastructureVersion: (selection: RowSelectionState) => void
}
export const DatastructureTab = (props: DatastructureTabProps) => {
  const { datasourceTitle, selectedVersionId, isReadOnly, isDatasourceInUse, onSelectDatastructureVersion } = props
  const t = useTranslations('datasources.dataModel.placeholder')
  const [isImportDatastructureModalOpen, setIsImportDatastructureModalOpen] = useState(false)

  // Parse the composite "datastructureId/versionId" prop
  const [selectedDatastructureId, selectedDatastructureVersionId] = selectedVersionId?.split('/') ?? [null, null]

  // Fetch the full version data (needed for UML model/styles)
  const { data: datastructureVersionData } = useGetDatastructureVersion({
    datastructureId: selectedDatastructureId || '',
    versionId: selectedDatastructureVersionId || '',
    isEnabled: !!selectedDatastructureId && !!selectedDatastructureVersionId,
  })

  const [datastructureVersion, setDatastructureVersion] = useState<DatastructureVersion | null>(null)

  useEffect(() => {
    if (!selectedDatastructureVersionId) {
      setDatastructureVersion(null)
    } else {
      setDatastructureVersion(datastructureVersionData?.data || null)
    }
  }, [selectedDatastructureVersionId, datastructureVersionData?.data])

  const { modelSessionManager, resetFormAndSession } = useDatastructureVersion({
    datastructureId: selectedDatastructureId || '',
    version: datastructureVersion,
    isCreateMode: false,
  })

  useEffect(() => {
    if (!datastructureVersion) resetFormAndSession(defaultDatastructureVersionFormData, null)
    else resetFormAndSession(mapDatastructureVersionApiToFormData(datastructureVersion), datastructureVersion)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [datastructureVersion])

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
              <GuardedLink className="underline" href="/datastructures">
                {chunks}
              </GuardedLink>
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
        canExportModel={false}
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
