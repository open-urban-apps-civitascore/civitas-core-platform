'use client'

import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useState } from 'react'

import { StructureDefinitionTab } from '@/app/(main)/datastructures/[datastructureId]/(versions)/components/structure-definition-tab/StructureDefinitionTab'
import { useDatastructureVersion } from '@/app/(main)/datastructures/[datastructureId]/(versions)/hooks/useDatastructureVersion'
import { useDatastructureCreation } from '@/app/(main)/datastructures/[datastructureId]/hooks/useDatastructureCreation'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import PageEditControls from '@/components/page-edit-controls/PageEditControls'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Form } from '@/components/ui/form'
import { Datasource, DATASOURCE_STATUS_TYPES, DatasourceStatusType, DatasourceTab } from '@/types/datasources'
import { Datastructure, DATASTRUCTURE_STATUS_TYPES, DatastructureVersion } from '@/types/datastructures'

import { useDatasourceForm } from '../hooks/useDatasourceForm'
import { BasicInfoTab } from './basic-info/BasicInfoTab'
import { ConnectorTab } from './connector-tab/ConnectorTab'

interface DatasourceOverviewProps {
  datasource: Datasource
  datastructure: Datastructure | null
  datastructureVersion: DatastructureVersion | null
}

const defaultDatastructure = {
  id: '',
  name: '',
  description: null,
  dataStructureStatus: DATASTRUCTURE_STATUS_TYPES.DRAFT,
  createdFromDataSource: true,
  assignments: [],
  inUse: false,
  createdAt: new Date().toISOString(),
  modifiedAt: new Date().toISOString(),
  dataStructureVersions: [],
}

const tabs: Tab<DatasourceTab>[] = [
  { value: 'basicInfo', label: 'datasources.tabs.basicInfo' },
  { value: 'connector', label: 'datasources.tabs.connector' },
  { value: 'dataStructure', label: 'datasources.tabs.dataStructure' },
  { value: 'accessPermissions', label: 'datasources.tabs.accessPermissions' },
]

const disabledTabs: DatasourceTab[] = ['accessPermissions']

export const DatasourceOverview = (props: DatasourceOverviewProps) => {
  const { datasource, datastructure: initialDatastructure, datastructureVersion } = props
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')
  const router = useRouter()
  const searchParams = useSearchParams()
  const pathname = usePathname()

  const [datastructure, setDatastructure] = useState<Datastructure>(initialDatastructure || defaultDatastructure)
  const [isReadOnly, setIsReadOnly] = useState(searchParams.get('mode') !== 'edit')

  useEffect(() => {
    setIsReadOnly(searchParams.get('mode') !== 'edit')
  }, [searchParams])

  const updateMode = useCallback(
    (isEditing: boolean) => {
      setIsReadOnly(!isEditing)
      const params = new URLSearchParams(searchParams.toString())
      if (isEditing) {
        params.set('mode', 'edit')
      } else {
        params.delete('mode')
      }
      const query = params.toString()
      router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false })
    },
    [pathname, searchParams, router],
  )

  const {
    form: DatasourceForm,
    readyConnectorType,
    dataSourceStatus,
    handleStatusChange,
    canSetAvailable,
    completedTabs,
    submitDatasource,
    isLoading: isLoadingDatasource,
  } = useDatasourceForm(datasource)

  const {
    isInUse,
    modelSessionManager,
    hasUserChanges: hasDatastructureBeenEdited,
  } = useDatastructureVersion({
    datastructureId: datastructure.id,
    version: datastructureVersion,
    isCreateMode: true,
  })

  const handleDatastructureCreation = (datastructureResponse: Datastructure) => setDatastructure(datastructureResponse)
  const { saveDatastructure: createDatastructure, isLoading: isCreatingDatastructure } = useDatastructureCreation()

  const [selectedTab, setSelectedTab] = useState<DatasourceTab>('basicInfo')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const handleSave = () => {
    submitDatasource(() => router.refresh())
  }

  const handleExit = () => {
    if (DatasourceForm.formState.isDirty) {
      setIsExitModalOpen(true)
    } else {
      DatasourceForm.reset()
      updateMode(false)
    }
  }

  const handleDiscardAndExit = () => {
    setIsExitModalOpen(false)
    DatasourceForm.reset()
    updateMode(false)
  }

  const handleSaveAndExit = async () => {
    if (hasDatastructureBeenEdited && !initialDatastructure) {
      const response = await createDatastructure()
    }
    submitDatasource(() => {
      setIsExitModalOpen(false)
      updateMode(false)
    })
  }

  const renderTabContent = () => {
    switch (selectedTab) {
      case 'basicInfo':
        return <BasicInfoTab form={DatasourceForm} isReadOnly={isReadOnly} />
      case 'connector':
        return <ConnectorTab form={DatasourceForm} readyConnectorType={readyConnectorType} isReadOnly={isReadOnly} />
      case 'dataStructure':
        return (
          <StructureDefinitionTab isReadOnly={isReadOnly} isInUse={isInUse} modelSessionManager={modelSessionManager} />
        )
      case 'accessPermissions':
      default:
        return null
    }
  }

  return (
    <PageContainer testId="datasourceOverviewPage" headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader
        title={datasource.name}
        segmentedControlBarProps={{
          tabs: tabs,
          selectedTab: selectedTab,
          onTabChange: setSelectedTab,
          completedTabs,
          disabledTabs,
          hasCompletionStatus: true,
        }}
        customElement={
          <PageEditControls<DatasourceStatusType>
            status={dataSourceStatus}
            onStatusChange={handleStatusChange}
            statusOptions={Object.values(DATASOURCE_STATUS_TYPES)}
            canSetAvailable={canSetAvailable}
            confirmButtonType="button"
            onConfirmClick={handleSave}
            isConfirmButtonDisabled={
              !DatasourceForm.formState.isDirty ||
              !!DatasourceForm.formState.errors.name ||
              (dataSourceStatus !== DATASOURCE_STATUS_TYPES.DRAFT &&
                Object.keys(DatasourceForm.formState.errors).length > 0) ||
              isLoadingDatasource
            }
            isCancelButtonDisabled={isLoadingDatasource}
            onCancelClick={handleExit}
            hasCard={false}
            isReadOnly={isReadOnly}
            onEditClick={() => updateMode(true)}
            cancelButtonTitle={tCommon('actions.exit')}
            wrapperClassname="w-auto"
          />
        }
      />
      <PageBackground className="overflow-y-auto" hasBackground={!isReadOnly}>
        <Form {...DatasourceForm}>
          <form
            data-testid="datasourceEditForm"
            aria-label={`${tCommon('form')} ${t('edit.basicInfo.title')}`}
            onSubmit={e => e.preventDefault()}
          >
            {isLoadingDatasource ? <LoadingSpinner className="h-[300px]" /> : renderTabContent()}
          </form>
        </Form>
      </PageBackground>

      <ExitWarningModal
        open={isExitModalOpen}
        isLoading={isLoadingDatasource}
        onOpenChange={setIsExitModalOpen}
        onDiscard={handleDiscardAndExit}
        onConfirm={handleSaveAndExit}
      />
    </PageContainer>
  )
}
