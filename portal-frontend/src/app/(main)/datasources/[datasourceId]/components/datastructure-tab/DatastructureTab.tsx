import { RowSelectionState } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import {
  defaultDatastructureVersionFormData,
  useDatastructureVersion,
} from '@/app/(main)/datastructures/[datastructureId]/(versions)/hooks/useDatastructureVersion'
import { useGetDatastructureVersion } from '@/app/services/api/datastructures/versions/clientRequests'
import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { InfoBox } from '@/components/text-box/TextBox'
import { UmlModeler } from '@/components/uml-modeler/UmlModeler'
import { usePermissions } from '@/hooks/use-permissions'
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { DatastructureVersion } from '@/types/datastructures'
import { mapDatastructureVersionApiToFormData } from '@/utils/datastructures'

import { DataModelImportModal } from './DataModelImportModal'

interface DatastructureTabProps {
  datasourceTitle: string
  selectedVersionId: string | null
  isReadOnly: boolean
  isDatasourceInUseByReleased: boolean
  isDatasourceReleased: boolean
  onSelectDatastructureVersion: (selection: RowSelectionState) => void
}
export const DatastructureTab = (props: DatastructureTabProps) => {
  const {
    datasourceTitle,
    selectedVersionId,
    isReadOnly,
    isDatasourceInUseByReleased,
    isDatasourceReleased,
    onSelectDatastructureVersion,
  } = props
  const t = useTranslations('datasources.dataModel.placeholder')
  const tRestricted = useTranslations('datasources.dataModel.restricted')
  const tDataModel = useTranslations('datasources.dataModel')
  const canImport = !isDatasourceInUseByReleased && !isDatasourceReleased && !isReadOnly
  const [isImportDatastructureModalOpen, setIsImportDatastructureModalOpen] = useState(false)

  // Parse the composite "datastructureId/versionId" prop
  const [selectedDatastructureId, selectedDatastructureVersionId] = selectedVersionId?.split('/') ?? [null, null]

  const { hasScopedPermission } = usePermissions()
  const canReadSelectedDatastructure =
    !!selectedDatastructureId &&
    hasScopedPermission(
      PERMISSION_NAMES.DATASTRUCTURE_READ,
      ASSIGNMENT_SCOPE_TYPES.DATASTRUCTURE,
      selectedDatastructureId,
    )
  const isSelectedDatastructureRestricted = !!selectedDatastructureVersionId && !canReadSelectedDatastructure

  // Fetch the full version data (needed for UML model/styles)
  const { data: datastructureVersionData } = useGetDatastructureVersion({
    datastructureId: selectedDatastructureId || '',
    versionId: selectedDatastructureVersionId || '',
    isEnabled: !!selectedDatastructureVersionId && canReadSelectedDatastructure,
  })

  const [datastructureVersion, setDatastructureVersion] = useState<DatastructureVersion | null>(null)

  useEffect(() => {
    if (!selectedDatastructureVersionId || !canReadSelectedDatastructure) {
      setDatastructureVersion(null)
    } else {
      setDatastructureVersion(datastructureVersionData?.data || null)
    }
  }, [selectedDatastructureVersionId, canReadSelectedDatastructure, datastructureVersionData?.data])

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

  const RestrictedPlaceholder = (
    <div className="text-center">
      <p className="text-xl text-foreground font-semibold mb-2">{tRestricted('title')}</p>
      <p className="text-muted-foreground text-sm">{tRestricted('description')}</p>
    </div>
  )

  return (
    <div className="flex h-full flex-col gap-4">
      {isDatasourceReleased && !isReadOnly && <InfoBox text={tDataModel('availableInfo')} />}
      <UmlModeler
        className="min-h-0"
        isReadOnly={true}
        modelSessionManager={modelSessionManager}
        isMultiSessionMode={false}
        canExportModel={false}
        placeHolder={isSelectedDatastructureRestricted ? RestrictedPlaceholder : UmlCanvasPlaceholder}
        onImportFromDatastructure={canImport ? handleImportFromDatastructure : undefined}
      />
      <DataModelImportModal
        datasourceTitle={datasourceTitle}
        open={isImportDatastructureModalOpen}
        selectedVersion={selectedVersionId}
        onSelectVersion={handleSelectVersion}
        onOpenChange={() => setIsImportDatastructureModalOpen(false)}
      />
    </div>
  )
}
