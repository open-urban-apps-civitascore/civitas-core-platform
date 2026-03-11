'use client'

import { RowSelectionState } from '@tanstack/react-table'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useState } from 'react'

import {
  defaultDatastructureVersionFormData,
  useDatastructureVersion,
} from '@/app/(main)/datastructures/[datastructureId]/(versions)/hooks/useDatastructureVersion'
import { useGetDatastructureVersion } from '@/app/services/api/datastructures/versions/clientRequests'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import PageEditControls from '@/components/page-edit-controls/PageEditControls'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Form } from '@/components/ui/form'
import { cn } from '@/lib/utils'
import { Datasource, DATASOURCE_STATUS_TYPES, DatasourceStatusType, DatasourceTab } from '@/types/datasources'
import { Datastructure, DatastructureVersion } from '@/types/datastructures'
import { mapDatastructureVersionApiToFormData } from '@/utils/datastructures'

import { useDatasourceForm } from '../hooks/useDatasourceForm'
import { BasicInfoTab } from './basic-info/BasicInfoTab'
import { ConnectorTab } from './connector-tab/ConnectorTab'
import { DatastructureTab } from './datastructure-tab/DatastructureTab'

interface DatasourceOverviewProps {
  datasource: Datasource
  datastructure: Datastructure | null
  datastructureVersion: DatastructureVersion | null
}

const tabs: Tab<DatasourceTab>[] = [
  { value: 'basicInfo', label: 'datasources.tabs.basicInfo' },
  { value: 'connector', label: 'datasources.tabs.connector' },
  { value: 'dataStructure', label: 'datasources.tabs.dataStructure' },
  { value: 'accessPermissions', label: 'datasources.tabs.accessPermissions' },
]

const disabledTabs: DatasourceTab[] = ['accessPermissions']

export const DatasourceOverview = (props: DatasourceOverviewProps) => {
  const { datasource, datastructure: initialDatastructure, datastructureVersion: initialDatastructureVersion } = props
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')
  const router = useRouter()
  const searchParams = useSearchParams()
  const pathname = usePathname()

  const [datastructureVersion, setDatastructureVersion] = useState<DatastructureVersion | null>(
    initialDatastructureVersion,
  )

  const [selectedDatastructureVersionId, setSelectedDatastructureVersionId] = useState<string | null>(
    initialDatastructureVersion?.id || null,
  )
  const [selectedDatastructureId, setSelectedDatastructureId] = useState<string | null>(
    initialDatastructure?.id || null,
  )

  const { data: datastructureVersionData } = useGetDatastructureVersion({
    datastructureId: selectedDatastructureId || '',
    versionId: selectedDatastructureVersionId || '',
    isEnabled:
      !!selectedDatastructureId &&
      !!selectedDatastructureVersionId &&
      initialDatastructureVersion?.id !== selectedDatastructureVersionId,
  })

  useEffect(() => {
    if (!selectedDatastructureVersionId) {
      setDatastructureVersion(null)
      return
    } else if (selectedDatastructureVersionId === initialDatastructureVersion?.id)
      setDatastructureVersion(initialDatastructureVersion)
    else setDatastructureVersion(datastructureVersionData?.data || null)
  }, [selectedDatastructureVersionId, datastructureVersionData?.data, initialDatastructureVersion])

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
    form: datasourceForm,
    readyConnectorType,
    dataSourceStatus,
    handleStatusChange,
    canSetAvailable,
    completedTabs,
    submitDatasource,
    isLoading: isLoadingDatasource,
  } = useDatasourceForm(datasource)

  const {
    modelSessionManager,
    hasUserChanges: hasDatastructureBeenEdited,
    resetToInitialState,
    resetFormAndSession,
    // saveDatastructureVersion,
  } = useDatastructureVersion({
    datastructureId: selectedDatastructureId || '',
    version: initialDatastructureVersion || null,
    isCreateMode: false,
  })

  // sets the active session with the new selected version's diagram or null if no version is selected
  useEffect(() => {
    const isInitialVersion = selectedDatastructureId === initialDatastructureVersion?.id
    if (isInitialVersion) {
      resetToInitialState()
      return
    } else {
      if (!datastructureVersion) resetFormAndSession(defaultDatastructureVersionFormData, null)
      else resetFormAndSession(mapDatastructureVersionApiToFormData(datastructureVersion), datastructureVersion)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [datastructureVersion, initialDatastructureVersion, selectedDatastructureId, selectedDatastructureVersionId])

  const [selectedTab, setSelectedTab] = useState<DatasourceTab>('basicInfo')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  // TODO: use this check when saving changes in datastructure version is implemented
  // const shouldSaveDatastructureVersion =
  // hasDatastructureBeenEdited && selectedDatastructureId && selectedDatastructureVersionId

  const handleSave = async () => {
    // TODO: use this function when implementing save datastructure version changes
    // if (shouldSaveDatastructureVersion) {
    //   const isDatastructureVersionSaved = await saveDatastructureVersion(selectedDatastructureId)
    //   if (!isDatastructureVersionSaved) return
    // }

    submitDatasource(() => router.refresh())
  }

  const handleExit = () => {
    if (datasourceForm.formState.isDirty) {
      setIsExitModalOpen(true)
    } else {
      datasourceForm.reset()
      updateMode(false)
    }
  }

  const handleDiscardAndExit = () => {
    setIsExitModalOpen(false)
    datasourceForm.reset()
    updateMode(false)
  }

  const handleSaveAndExit = async () => {
    // TODO: use this function when implementing save datastructure version changes
    // if (shouldSaveDatastructureVersion) {
    //   const isDatastructureVersionSaved = await saveDatastructureVersion(selectedDatastructureId)
    //   if (!isDatastructureVersionSaved) return
    // }

    submitDatasource(() => {
      setIsExitModalOpen(false)
      updateMode(false)
    })
  }

  const handleSelectDatastructureVersion = (selection: RowSelectionState) => {
    const [selectedDatastructureId, selectedVersionId] = Object.keys(selection)[0]?.split('/') || [null, null]
    setSelectedDatastructureId(selectedDatastructureId)
    setSelectedDatastructureVersionId(selectedVersionId)
    datasourceForm.setValue('dataStructureVersionId', selectedVersionId, {
      shouldDirty: true,
      shouldValidate: true,
    })
  }

  const isConfirmButtonDisabled =
    !(datasourceForm.formState.isDirty || hasDatastructureBeenEdited) ||
    !!datasourceForm.formState.errors.name ||
    (dataSourceStatus !== DATASOURCE_STATUS_TYPES.DRAFT && Object.keys(datasourceForm.formState.errors).length > 0) ||
    isLoadingDatasource

  const renderTabContent = () => {
    switch (selectedTab) {
      case 'basicInfo':
        return <BasicInfoTab form={datasourceForm} isReadOnly={isReadOnly} />
      case 'connector':
        return <ConnectorTab form={datasourceForm} readyConnectorType={readyConnectorType} isReadOnly={isReadOnly} />
      case 'dataStructure':
        return (
          <DatastructureTab
            datasourceTitle={datasource.name}
            selectedVersionId={
              selectedDatastructureId && selectedDatastructureVersionId
                ? `${selectedDatastructureId}/${selectedDatastructureVersionId}`
                : null
            }
            onSelectDatastructureVersion={handleSelectDatastructureVersion}
            isReadOnly={isReadOnly}
            isInUse={!!initialDatastructureVersion?.inUse}
            modelSessionManager={modelSessionManager}
          />
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
            isConfirmButtonDisabled={isConfirmButtonDisabled}
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
        <Form {...datasourceForm}>
          <form
            data-testid="datasourceEditForm"
            aria-label={`${tCommon('form')} ${t('edit.basicInfo.title')}`}
            onSubmit={e => e.preventDefault()}
            className={cn(selectedTab === 'dataStructure' && 'h-full')}
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
