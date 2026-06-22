'use client'

import { RowSelectionState } from '@tanstack/react-table'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useMemo, useState } from 'react'

import { useGetDatapools } from '@/app/services/api/datapools/clientRequests'
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
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { cn } from '@/lib/utils'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { Datapool } from '@/types/datapools'
import {
  DATAPOOL_SCOPE_TYPES,
  Datasource,
  DATASOURCE_STATUS_TYPES,
  DatasourceStatusType,
  DatasourceTab,
} from '@/types/datasources'
import { getSelectedDatastructureVersion } from '@/utils/datasources'

import { useDatasourceForm } from '../hooks/useDatasourceForm'
import { AccessManagementTab } from './access-management/AccessManagementTab'
import { BasicInfoTab } from './basic-info/BasicInfoTab'
import { ConnectorTab } from './connector-tab/ConnectorTab'
import { AddDatapoolModal } from './datapools-tab/AddDatapoolModal'
import { DatapoolsTab } from './datapools-tab/DatapoolsTab'
import { DatastructureTab } from './datastructure-tab/DatastructureTab'

interface DatasourceOverviewProps {
  datasource: Datasource
  initialAssignments: GroupRoleAssignmentTable[]
}

const allTabs: Tab<DatasourceTab>[] = [
  { value: 'basicInfo', label: 'datasources.tabs.basicInfo' },
  { value: 'connector', label: 'datasources.tabs.connector' },
  { value: 'dataStructure', label: 'datasources.tabs.dataStructure' },
  { value: 'datapools', label: 'datasources.tabs.datapools' },
  { value: 'accessManagement', label: 'datasources.tabs.accessManagement' },
]

export const DatasourceOverview = (props: DatasourceOverviewProps) => {
  const { datasource, initialAssignments } = props
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')
  const { hasPermission, hasScopedPermission } = usePermissions()
  const canUpdate = hasScopedPermission(PERMISSION_NAMES.DATASOURCE_UPDATE, 'DATASOURCE', datasource.id)
  const canRelease = hasScopedPermission(PERMISSION_NAMES.DATASOURCE_RELEASE, 'DATASOURCE', datasource.id)
  const canReadDatastructures = hasPermission(PERMISSION_NAMES.DATASTRUCTURE_READ)
  const router = useRouter()
  const searchParams = useSearchParams()
  const pathname = usePathname()
  const [assignedGroups, setAssignedGroups] = useState<GroupRoleAssignmentTable[]>(initialAssignments)

  const scopedDatapoolIds =
    datasource.datapoolScope?.type === DATAPOOL_SCOPE_TYPES.SPECIFIC ? datasource.datapoolScope.datapoolIds : []

  const scopedDatapoolIdsKey = scopedDatapoolIds.join(',')

  const datapoolIdsParam = useMemo(() => {
    if (!scopedDatapoolIdsKey) return undefined
    const params = new URLSearchParams()
    params.set('id', scopedDatapoolIdsKey)
    return params
  }, [scopedDatapoolIdsKey])

  const { data: initialDatapoolsResponse, isLoading: isLoadingDatapools } = useGetDatapools({
    params: datapoolIdsParam,
    isEnabled: scopedDatapoolIds.length > 0,
  })

  const initialDatapools = useMemo<Datapool[]>(
    () => (scopedDatapoolIds.length > 0 ? (initialDatapoolsResponse?.data ?? []) : []),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [initialDatapoolsResponse, scopedDatapoolIdsKey],
  )
  const [assignedDatapools, setAssignedDatapools] = useState<Datapool[]>([])
  const [isAddDatapoolModalOpen, setIsAddDatapoolModalOpen] = useState(false)
  const [isReadOnly, setIsReadOnly] = useState(searchParams.get('mode') !== 'edit')

  // Derive initial IDs from the datasource's linked version summary
  const initialDatastructureId = datasource.dataStructureVersion?.dataStructureId ?? null
  const initialVersionId = datasource.dataStructureVersion?.id ?? null

  const [selectedDatastructureVersionId, setSelectedDatastructureVersionId] = useState<string | null>(initialVersionId)
  const [selectedDatastructureId, setSelectedDatastructureId] = useState<string | null>(initialDatastructureId)

  useEffect(() => {
    setIsReadOnly(searchParams.get('mode') !== 'edit')
  }, [searchParams])

  useEffect(() => {
    setAssignedDatapools(initialDatapools)
  }, [initialDatapools])

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
    areDatapoolsDirty,
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
  } = useDatasourceForm(datasource, assignedGroups, initialAssignments, assignedDatapools, initialDatapools)

  const [selectedTab, setSelectedTab] = useState<DatasourceTab>('basicInfo')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  const hasUnsavedChanges =
    datasourceForm.formState.isDirty || areAssignmentsDirty || areDatapoolsDirty || hasStatusChanged

  const tabs = useMemo(
    () => allTabs.filter(tab => tab.value !== 'dataStructure' || canReadDatastructures),
    [canReadDatastructures],
  )

  const resetToInitialState = () => {
    resetDatasourceToInitialState()
    datasourceForm.reset()
    setAssignedGroups(initialAssignments)
    setAssignedDatapools(initialDatapools)
    setSelectedDatastructureId(initialDatastructureId)
    setSelectedDatastructureVersionId(initialVersionId)
  }
  const handleSave = () =>
    submitDatasource(() => {
      setAssignedGroups(prev => prev.filter(g => g.assignedRoles.length > 0))
      router.refresh()
    })

  const handleSaveForUnsavedChanges = () =>
    submitDatasource(() => {
      setAssignedGroups(prev => prev.filter(g => g.assignedRoles.length > 0))
    })

  useRegisterUnsavedChanges(hasUnsavedChanges, handleSaveForUnsavedChanges)

  const handleExit = () => {
    if (hasUnsavedChanges) {
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
    await submitDatasource(() => {
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
    !hasUnsavedChanges ||
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
          />
        )
      case 'datapools':
        return (
          <DatapoolsTab
            form={datasourceForm}
            isReadOnly={isReadOnly}
            assignedDatapools={assignedDatapools}
            isLoadingDatapools={isLoadingDatapools}
            onDeleteDatapool={id => setAssignedDatapools(prev => prev.filter(dp => dp.id !== id))}
            onOpenAddModal={() => setIsAddDatapoolModalOpen(true)}
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
          tabsWithNoCompletionStatus: ['accessManagement', 'datapools'], // Access Management and Datapools tabs have no required fields, so they should not show completion status
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
            {isLoadingDatasource ? <LoadingSpinner className="h-75" /> : renderTabContent()}
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

      <AddDatapoolModal
        open={isAddDatapoolModalOpen}
        onOpenChange={setIsAddDatapoolModalOpen}
        assignedDatapoolIds={assignedDatapools.map(dp => dp.id)}
        onAddDatapools={newDatapools => setAssignedDatapools(prev => [...prev, ...newDatapools])}
      />
    </PageContainer>
  )
}
