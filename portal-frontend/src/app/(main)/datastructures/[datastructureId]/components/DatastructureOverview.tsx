'use client'

import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useState } from 'react'

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
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import { PERMISSION_NAMES } from '@/types/currentUser'
import {
  Datastructure,
  DATASTRUCTURE_STATUS_TYPES,
  DatastructureStatusType,
  DatastructureTab,
} from '@/types/datastructures'
import { mapDatastructureVersionsApiToListData } from '@/utils/datastructures'

import { useDatastructure } from '../hooks/useDatastructure'
import { AccessManagementTab } from './access-management-tab/AccessManagementTab'
import { BasicInfoTab } from './basic-info-tab/BasicInfoTab'
import { VersionsTab } from './versions-tab/VersionsTab'

export const tabs: Tab<DatastructureTab>[] = [
  {
    value: 'basicInfo',
    label: 'datastructures.tabs.basicInfo',
  },
  {
    value: 'versions',
    label: 'datastructures.tabs.versions',
  },
  {
    value: 'accessManagement',
    label: 'datastructures.tabs.accessManagement',
  },
]

interface DatastructureOverviewProps {
  datastructure: Datastructure
  initialAssignments: GroupRoleAssignmentTable[]
}

export const DatastructureOverview = (props: DatastructureOverviewProps) => {
  const { datastructure, initialAssignments } = props
  const t = useTranslations('datastructures')
  const searchParams = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()

  const tCommon = useTranslations('common')
  const { hasScopedPermission } = usePermissions()
  const canUpdate = hasScopedPermission(
    PERMISSION_NAMES.DATASTRUCTURE_UPDATE,
    ASSIGNMENT_SCOPE_TYPES.DATASTRUCTURE,
    datastructure.id,
  )
  const canRelease = hasScopedPermission(
    PERMISSION_NAMES.DATASTRUCTURE_RELEASE,
    ASSIGNMENT_SCOPE_TYPES.DATASTRUCTURE,
    datastructure.id,
  )

  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isReadOnly, setIsReadOnly] = useState(searchParams.get('mode') !== 'edit')
  const [assignedGroups, setAssignedGroups] = useState<GroupRoleAssignmentTable[]>(initialAssignments)

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
    canStage,
    completedTabs,
    form: datastructureForm,
    handleStatusChange,
    isConfirmButtonDisabled,
    isLoading,
    resetToInitialState,
    saveDatastructure,
    selectedTab,
    canSetDraft,
    setSelectedTab,
    statusHint,
    statusAvailableHint,
    datastructureStatus,
  } = useDatastructure({ datastructure, assignedGroups, initialAssignments, canRelease })

  const hasUnsavedChanges = datastructureForm.formState.isDirty || areAssignmentsDirty

  const exitEditMode = () => {
    resetToInitialState()
    setAssignedGroups(initialAssignments)
    updateMode(false)
  }

  const handleDiscardAndExit = () => {
    exitEditMode()
    setIsExitModalOpen(false)
  }

  const handleSave = async (shouldRefresh = true): Promise<boolean> => {
    const isSaved = await saveDatastructure(shouldRefresh)
    if (isSaved) {
      setAssignedGroups(prev => prev.filter(g => g.assignedRoles.length > 0))
    }
    return isSaved
  }

  const handleSaveAndExit = async (): Promise<boolean> => {
    const isSaved = await handleSave()
    if (isSaved) {
      setIsExitModalOpen(false)
      updateMode(false)
    }
    return isSaved
  }

  const handleExit = () => {
    if (datastructureForm.formState.isDirty || areAssignmentsDirty) setIsExitModalOpen(true)
    else exitEditMode()
  }

  // shouldRefresh === false when navigating away. The router refresh is done by the
  // UnsavedChangesProvider after the push (a refresh before the push would cancel the navigation).
  useRegisterUnsavedChanges(hasUnsavedChanges, () => handleSave(false), true)

  const renderTabContent = () => {
    switch (selectedTab) {
      case 'basicInfo':
        return <BasicInfoTab form={datastructureForm} isReadOnly={isReadOnly} />
      case 'versions':
        return (
          <VersionsTab
            datastructureId={datastructure.id}
            versions={mapDatastructureVersionsApiToListData(datastructure.dataStructureVersions)}
            rowCount={datastructure.dataStructureVersions.length}
            isReadOnly={isReadOnly}
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
    <PageContainer testId="datastructureOverviewPage" headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader
        title={datastructure.name}
        segmentedControlBarProps={{
          tabs,
          selectedTab: selectedTab,
          onTabChange: setSelectedTab,
          completedTabs,
          tabsWithNoCompletionStatus: ['accessManagement'],
          hasCompletionStatus: true,
        }}
        customElement={
          <PageEditControls<DatastructureStatusType>
            status={datastructureStatus}
            onStatusChange={handleStatusChange}
            statusOptions={Object.values(DATASTRUCTURE_STATUS_TYPES)}
            canStage={canStage}
            canRelease={canRelease}
            statusHint={statusHint}
            availableHint={statusAvailableHint}
            confirmButtonType="button"
            onConfirmClick={() => handleSave()}
            isConfirmButtonDisabled={isConfirmButtonDisabled}
            isCancelButtonDisabled={isLoading}
            onCancelClick={handleExit}
            hasCard={false}
            canEdit={
              datastructure.dataStructureStatus === DATASTRUCTURE_STATUS_TYPES.AVAILABLE
                ? canUpdate && canRelease
                : canUpdate
            }
            isReadOnly={isReadOnly}
            onEditClick={() => updateMode(true)}
            cancelButtonTitle={tCommon('actions.exit')}
            wrapperClassname="w-auto"
            canSetDraft={canSetDraft}
          />
        }
      />
      <PageBackground className="overflow-y-auto" hasBackground={!isReadOnly}>
        <Form {...datastructureForm}>
          <form
            data-testid="datastructureEditForm"
            aria-label={`${tCommon('form')} ${t('edit.basicInfo.title')}`}
            onSubmit={e => e.preventDefault()}
          >
            {isLoading ? <LoadingSpinner className="h-[300px]" /> : renderTabContent()}
          </form>
        </Form>
      </PageBackground>

      <ExitWarningModal
        open={isExitModalOpen}
        isLoading={isLoading}
        onOpenChange={setIsExitModalOpen}
        onDiscard={handleDiscardAndExit}
        onConfirm={handleSaveAndExit}
      />
    </PageContainer>
  )
}
