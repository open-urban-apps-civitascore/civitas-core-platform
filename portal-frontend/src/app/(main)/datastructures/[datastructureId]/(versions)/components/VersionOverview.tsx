'use client'

import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useWatch } from 'react-hook-form'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { StatusDropdown } from '@/components/status-dropdown/StatusDropdown'
import { Button } from '@/components/ui/button'
import { QUERY_PARAMS } from '@/const/searchParams'
import { useError } from '@/hooks/use-error'
import { usePermissions } from '@/hooks/use-permissions'
import { useQueryParams } from '@/hooks/use-query-params'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import { PERMISSION_NAMES } from '@/types/currentUser'
import {
  Datastructure,
  DATASTRUCTURE_STATUS_TYPES,
  DatastructureVersion,
  DatastructureVersionFormAvailableSchema,
  DatastructureVersionTab,
} from '@/types/datastructures'
import { getHeaderAction } from '@/utils/headerAction'

import { useDatastructureVersion } from '../hooks/useDatastructureVersion'
import { StructureDefinitionTab } from './structure-definition-tab/StructureDefinitionTab'
import { VersionInfoTab } from './version-info-tab/VersionInfoTab'

const tabs: Tab<DatastructureVersionTab>[] = [
  {
    value: 'versionInfo',
    label: 'datastructureVersions.tabs.versionInfo',
  },
  {
    value: 'structure',
    label: 'datastructureVersions.tabs.structure',
  },
]

const DEFAULT_TAB = tabs[0]

interface VersionOverviewProps {
  title: string
  datastructure: Datastructure
  version: DatastructureVersion | null
  isCreateMode: boolean
  testId: string
}

export const VersionOverview = (props: VersionOverviewProps) => {
  const { title, version, datastructure, isCreateMode, testId } = props
  const datastructureId = datastructure.id
  const params = useSearchParams()
  const mode = params.get('mode')
  const t = useTranslations('datastructureVersions')
  const tCommon = useTranslations('common')
  const router = useRouter()
  const { setSubTabValueParam, subTabValue } = useQueryParams()
  const { handleFormValidationError } = useError()
  const { hasScopedPermission } = usePermissions()
  const canUpdate = hasScopedPermission(
    PERMISSION_NAMES.DATASTRUCTURE_UPDATE,
    ASSIGNMENT_SCOPE_TYPES.DATASTRUCTURE,
    datastructureId,
  )
  const canRelease = hasScopedPermission(
    PERMISSION_NAMES.DATASTRUCTURE_RELEASE,
    ASSIGNMENT_SCOPE_TYPES.DATASTRUCTURE,
    datastructureId,
  )

  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isReadOnly, setIsReadOnly] = useState(mode !== 'edit')
  const [canStage, setCanSetAvailable] = useState(true)

  const isInUse = version?.inUse || false

  const otherVersions = !version
    ? datastructure.dataStructureVersions
    : datastructure.dataStructureVersions?.flatMap(datastructureVersion =>
        datastructureVersion.id !== version.id ? datastructureVersion : [],
      )

  const isDatastructureAvailable = datastructure.dataStructureStatus === DATASTRUCTURE_STATUS_TYPES.AVAILABLE
  const isLastAvailableVersionInAvailableDatastructure =
    isDatastructureAvailable &&
    !otherVersions.some(
      otherVersion => otherVersion.dataStructureVersionStatus === DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
    )

  const redirectAfterVersionCreation = (createResponse: DatastructureVersion) =>
    router.push(
      `/datastructures/${datastructureId}/${createResponse.id}?mode=edit&${QUERY_PARAMS.subTabValue}=${subTabValue}`,
    )

  const {
    form,
    isLoading,
    modelSessionManager,
    resetToInitialState,
    saveDatastructureVersion,
    handleStatusChange,
    hasUserChanges,
  } = useDatastructureVersion({
    version,
    isCreateMode,
    datastructureId: datastructure.id,
    dataStructureName: datastructure.name,
    onCreateVersion: redirectAfterVersionCreation,
    canStage,
  })

  const formValues = useWatch({ control: form.control })
  const descriptionWatch = form.watch('description')
  const versionWatch = form.watch('version')
  const modelNameWatch = form.watch('modelName')
  const sourceWatch = form.watch('dataStructureVersionSource')

  const statusWatch = form.watch('dataStructureVersionStatus')
  const nodesWatch = form.watch('nodes')

  const completedTabs = useMemo((): DatastructureVersionTab[] => {
    const completed: DatastructureVersionTab[] = []
    if (versionWatch.length > 0 && descriptionWatch.length > 0 && sourceWatch) completed.push('versionInfo')
    if (nodesWatch.length > 0 && modelNameWatch) completed.push('structure')
    return completed
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [formValues])

  const canSetDraft = !isInUse && !isLastAvailableVersionInAvailableDatastructure

  useEffect(() => {
    setCanSetAvailable(DatastructureVersionFormAvailableSchema.safeParse(formValues).success)
  }, [formValues])

  const handleSubmit = async () => {
    let isSaved = false

    await form.handleSubmit(
      async () => {
        isSaved = await saveDatastructureVersion(datastructure.id)
      },
      errors => {
        handleFormValidationError(errors)
        isSaved = false
      },
    )()

    return isSaved
  }

  useRegisterUnsavedChanges(hasUserChanges, handleSubmit)

  const handleSave = async () => {
    const isSaved = await handleSubmit()
    if (isSaved) {
      setIsExitModalOpen(false)
    }
  }

  const exitEditMode = () => {
    setIsExitModalOpen(false)
    router.push(`/datastructures/${datastructureId}`)
  }

  const handleExitWarningSave = async () => {
    const isSaved = await handleSubmit()
    if (isSaved) {
      exitEditMode()
    } else {
      setIsExitModalOpen(false)
    }
  }

  const handleExit = () => {
    resetToInitialState()
    exitEditMode()
  }

  const handleExitButtonClick = () => {
    if (hasUserChanges) setIsExitModalOpen(true)
    else handleExit()
  }

  const versionAlreadyExistsError = useMemo(() => {
    const versionExists = otherVersions.find(otherVersion => otherVersion.version === versionWatch.trim())
    if (versionExists) {
      return t('errors.versionAlreadyExists')
    }
    return undefined
  }, [otherVersions, versionWatch, t])

  const statusHint = useMemo(() => {
    if (isInUse) return t('messages.isInUseStatusHint')
    if (isLastAvailableVersionInAvailableDatastructure) return t('messages.isLastAvailableVersion')
    return undefined
  }, [isInUse, isLastAvailableVersionInAvailableDatastructure, t])

  const isConfirmButtonDisabled =
    !hasUserChanges || !!form.formState.errors.version || !!versionAlreadyExistsError || isLoading

  const ActionButtonsAndStatusSwitch = (
    <div className="flex gap-6">
      <StatusDropdown
        statusOptions={Object.values(DATASTRUCTURE_STATUS_TYPES)}
        status={statusWatch}
        onStatusChange={handleStatusChange}
        canStage={canStage}
        canSetDraft={canSetDraft}
        canRelease={canRelease}
        statusHint={statusHint}
        availableHint={!canRelease ? tCommon('messages.releasePermissionRequiredHint') : undefined}
      />
      <ActionButtons
        confirmButtonType="button"
        onCancelClick={handleExitButtonClick}
        onConfirmClick={handleSave}
        isConfirmButtonDisabled={isConfirmButtonDisabled}
        isCancelButtonDisabled={isLoading}
        cancelButtonTitle={tCommon('actions.exit')}
        hasCard={false}
        wrapperClassname="w-auto"
      />
    </div>
  )

  const EditButton = (
    <Button data-testid="editButton" type="button" onClick={() => setIsReadOnly(false)}>
      {tCommon('actions.edit')}
    </Button>
  )

  const renderTabContent = () => {
    switch (subTabValue) {
      case 'structure':
        return (
          <StructureDefinitionTab isReadOnly={isReadOnly} modelSessionManager={modelSessionManager} isInUse={isInUse} />
        )
      case 'versionInfo':
      default:
        return (
          <VersionInfoTab form={form} isReadOnly={isReadOnly} versionAlreadyExistsError={versionAlreadyExistsError} />
        )
    }
  }

  return (
    <PageContainer testId={testId} headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader
        title={title}
        segmentedControlBarProps={{
          tabs: tabs,
          selectedTab: subTabValue || DEFAULT_TAB.value,
          onTabChange: selectedTab => setSubTabValueParam(selectedTab),
          completedTabs,
          hasCompletionStatus: true,
        }}
        customElement={getHeaderAction({
          isReadOnly,
          canUpdate:
            version?.dataStructureVersionStatus === DATASTRUCTURE_STATUS_TYPES.AVAILABLE
              ? canUpdate && canRelease
              : canUpdate,
          editButton: EditButton,
          saveExitButtons: ActionButtonsAndStatusSwitch,
        })}
      />
      <PageBackground className="overflow-y-auto" hasBackground={!isReadOnly}>
        {isLoading ? <LoadingSpinner className="h-full" /> : renderTabContent()}
      </PageBackground>

      <ExitWarningModal
        open={isExitModalOpen}
        onOpenChange={() => setIsExitModalOpen(false)}
        onDiscard={handleExit}
        onConfirm={handleExitWarningSave}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
