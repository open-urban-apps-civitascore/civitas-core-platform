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
import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import PageEditControls from '@/components/page-edit-controls/PageEditControls'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Form } from '@/components/ui/form'
import { usePermissions } from '@/hooks/use-permissions'
import { cn } from '@/lib/utils'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { Datasource, DATASOURCE_STATUS_TYPES, DatasourceStatusType, DatasourceTab } from '@/types/datasources'
import { Datastructure, DatastructureVersion } from '@/types/datastructures'
import { getSelectedDatastructureVersion } from '@/utils/datasources'
import { mapDatastructureVersionApiToFormData } from '@/utils/datastructures'

import { useDatasourceForm } from '../hooks/useDatasourceForm'
import { AccessManagementTab } from './access-management/AccessManagementTab'
import { BasicInfoTab } from './basic-info/BasicInfoTab'
import { ConnectorTab } from './connector-tab/ConnectorTab'
import { DatastructureTab } from './datastructure-tab/DatastructureTab'

interface DatasourceOverviewProps {
  datasource: Datasource
  datastructure: Datastructure | null
  datastructureVersion: DatastructureVersion | null
  initialAssignments: GroupRoleAssignmentTable[]
}

const tabs: Tab<DatasourceTab>[] = [
  { value: 'basicInfo', label: 'datasources.tabs.basicInfo' },
  { value: 'connector', label: 'datasources.tabs.connector' },
  { value: 'dataStructure', label: 'datasources.tabs.dataStructure' },
  { value: 'accessManagement', label: 'datasources.tabs.accessManagement' },
]
export const DatasourceOverview = (props: DatasourceOverviewProps) => {
  const {
    datasource,
    datastructure: initialDatastructure,
    datastructureVersion: initialDatastructureVersion,
    initialAssignments,
  } = props
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')
  const { hasScopedPermission } = usePermissions()
  const canUpdate = hasScopedPermission(PERMISSION_NAMES.DATASOURCE_UPDATE, 'DATASOURCE', datasource.id)
  const canRelease = hasScopedPermission(PERMISSION_NAMES.DATASOURCE_RELEASE, 'DATASOURCE', datasource.id)
  const router = useRouter()
  const searchParams = useSearchParams()
  const pathname = usePathname()
  const [assignedGroups, setAssignedGroups] = useState<GroupRoleAssignmentTable[]>(initialAssignments)
  const [isReadOnly, setIsReadOnly] = useState(searchParams.get('mode') !== 'edit')

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
    areAssignmentsDirty,
    form: datasourceForm,
    dataSourceStatus,
    selectedConnectorType,
    hasStatusChanged,
    handleStatusChange,
    canSetAvailable,
    completedTabs,
    submitDatasource,
    resetToInitialState: resetDatasourceToInitialState,
    isLoading: isLoadingDatasource,
  } = useDatasourceForm(datasource, assignedGroups, initialAssignments)

  const {
    modelSessionManager,
    hasUserChanges: hasDatastructureBeenEdited,
    resetToInitialState: resetToInitialDatastructureState,
    resetFormAndSession,
    // saveDatastructureVersion,
  } = useDatastructureVersion({
    datastructureId: selectedDatastructureId || '',
    version: initialDatastructureVersion || null,
    isCreateMode: false,
  })

  // sets the active session with the new selected version's diagram or null if no version is selected
  useEffect(() => {
    const isInitialVersion = selectedDatastructureVersionId === initialDatastructureVersion?.id
    if (isInitialVersion) {
      resetToInitialDatastructureState()
      return
    } else {
      if (!datastructureVersion) resetFormAndSession(defaultDatastructureVersionFormData, null)
      else resetFormAndSession(mapDatastructureVersionApiToFormData(datastructureVersion), datastructureVersion)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [datastructureVersion, initialDatastructureVersion, selectedDatastructureId, selectedDatastructureVersionId])

  const [selectedTab, setSelectedTab] = useState<DatasourceTab>('basicInfo')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  const resetToInitialState = () => {
    resetDatasourceToInitialState()
    resetToInitialDatastructureState()
    datasourceForm.reset()
    setAssignedGroups(initialAssignments)
    setSelectedDatastructureId(initialDatastructure?.id || null)
    setSelectedDatastructureVersionId(initialDatastructureVersion?.id || null)
  }
  const handleSave = async () => {
    // TODO: use this function when implementing save datastructure version changes
    // if (shouldSaveDatastructureVersion) {
    //   const isDatastructureVersionSaved = await saveDatastructureVersion(selectedDatastructureId)
    //   if (!isDatastructureVersionSaved) return
    // }
    submitDatasource(() => {
      setAssignedGroups(prev => prev.filter(g => g.assignedRoles.length > 0))
      router.refresh()
    })
  }
  const handleExit = () => {
    if (datasourceForm.formState.isDirty || areAssignmentsDirty || hasDatastructureBeenEdited || hasStatusChanged) {
      setIsExitModalOpen(true)
    } else {
      resetToInitialState()
      updateMode(false)
      setAssignedGroups(initialAssignments)
    }
  }

  const handleDiscardAndExit = () => {
    setIsExitModalOpen(false)
    updateMode(false)
    resetToInitialState()
  }

  const handleSaveAndExit = async () => {
    // TODO: use this function when implementing save datastructure version changes
    // if (shouldSaveDatastructureVersion) {
    //   const isDatastructureVersionSaved = await saveDatastructureVersion(selectedDatastructureId)
    //   if (!isDatastructureVersionSaved) return
    // }

    submitDatasource(() => {
      setAssignedGroups(prev => prev.filter(g => g.assignedRoles.length > 0))
      setIsExitModalOpen(false)
      updateMode(false)
    })
  }

  const handleSelectDatastructureVersion = (selection: RowSelectionState) => {
    const { datastructureId, versionId } = getSelectedDatastructureVersion(selection)
    const hasSelectionChanged =
      datastructureId !== selectedDatastructureId || versionId !== selectedDatastructureVersionId

    setSelectedDatastructureId(datastructureId)
    setSelectedDatastructureVersionId(versionId)

    datasourceForm.setValue('dataStructureVersionId', versionId, {
      shouldDirty: hasSelectionChanged,
      shouldValidate: true,
    })
  }

  const isConfirmButtonDisabled =
    !(datasourceForm.formState.isDirty || hasDatastructureBeenEdited || areAssignmentsDirty || hasStatusChanged) ||
    !!datasourceForm.formState.errors.name ||
    (dataSourceStatus !== DATASOURCE_STATUS_TYPES.DRAFT && Object.keys(datasourceForm.formState.errors).length > 0) ||
    isLoadingDatasource

  const renderTabContent = () => {
    switch (selectedTab) {
      case 'basicInfo':
        return <BasicInfoTab form={datasourceForm} isReadOnly={isReadOnly} />
      case 'connector':
        return <ConnectorTab form={datasourceForm} connectorType={selectedConnectorType} isReadOnly={isReadOnly} />
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
            isDatasourceInUse={datasource.inUse}
            modelSessionManager={modelSessionManager}
          />
        )
      case 'accessManagement':
        return (
          <AccessManagementTab
            assignedGroups={assignedGroups}
            onAssignedGroupsChange={setAssignedGroups}
            isReadOnly={isReadOnly}
          />
        )
      default:
        return null
    }
  }

  return (
    <PageContainer testId="datasourceOverviewPage" headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader
        title={datasource.name}
        segmentedControlBarProps={{
          tabs,
          selectedTab: selectedTab,
          onTabChange: setSelectedTab,
          completedTabs,
          tabsWithNoCompletionStatus: ['accessManagement'], // Access Management tab has no required fields, so it should not show completion status
          hasCompletionStatus: true,
        }}
        customElement={
          <PageEditControls<DatasourceStatusType>
            status={dataSourceStatus}
            onStatusChange={handleStatusChange}
            statusOptions={Object.values(DATASOURCE_STATUS_TYPES)}
            canSetAvailable={canSetAvailable}
            canRelease={canRelease}
            confirmButtonType="button"
            onConfirmClick={handleSave}
            isConfirmButtonDisabled={isConfirmButtonDisabled}
            isCancelButtonDisabled={isLoadingDatasource}
            onCancelClick={handleExit}
            hasCard={false}
            canEdit={
              datasource.dataSourceStatus === DATASOURCE_STATUS_TYPES.AVAILABLE ? canUpdate && canRelease : canUpdate
            }
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
