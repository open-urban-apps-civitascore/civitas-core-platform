'use client'

import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { InfoModal } from '@/components/modals/info-modal/InfoModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import PageEditControls from '@/components/page-edit-controls/PageEditControls'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
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
  DatastructureStatusType,
  DatastructureVersion,
  DatastructureVersionFormAvailableSchema,
  DatastructureVersionTab,
} from '@/types/datastructures'

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
  const pathname = usePathname()

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

  const isVersionAvailable = version?.dataStructureVersionStatus === DATASTRUCTURE_STATUS_TYPES.AVAILABLE

  const canEdit = isVersionAvailable ? canUpdate && canRelease : canUpdate

  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isDraftBlockedModalOpen, setIsDraftBlockedModalOpen] = useState(false)
  const [isReadOnly, setIsReadOnly] = useState(mode !== 'edit' || !canEdit)

  const isInUseByReleased = version?.inUseByReleased ?? false

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
  })

  const formValues = useWatch({ control: form.control })
  const descriptionWatch = form.watch('description')
  const modelNameWatch = form.watch('modelName')
  const sourceWatch = form.watch('dataStructureVersionSource')

  const statusWatch = form.watch('dataStructureVersionStatus')
  const nodesWatch = form.watch('nodes')

  const completedTabs = useMemo((): DatastructureVersionTab[] => {
    const completed: DatastructureVersionTab[] = []
    // The version is assigned by the registry, so completeness rests only on what a user supplies.
    if (descriptionWatch.length > 0 && sourceWatch) completed.push('versionInfo')
    if (nodesWatch.length > 0 && modelNameWatch) completed.push('structure')
    return completed
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [formValues])

  const canSetDraft = !isLastAvailableVersionInAvailableDatastructure

  const canStage = useMemo(() => DatastructureVersionFormAvailableSchema.safeParse(formValues).success, [formValues])

  useEffect(() => {
    if (isVersionAvailable && !isReadOnly) toast.info(t('messages.isAvailableModelHint'))
  }, [isVersionAvailable, isReadOnly, t])

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

  const updateMode = useCallback(
    (isEditing: boolean) => {
      setIsReadOnly(!isEditing)
      const searchParams = new URLSearchParams(params.toString())
      if (isEditing) {
        searchParams.set('mode', 'edit')
      } else {
        searchParams.delete('mode')
      }
      const query = searchParams.toString()
      router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false })
    },
    [pathname, params, router],
  )

  const statusHint = isLastAvailableVersionInAvailableDatastructure ? t('messages.isLastAvailableVersion') : undefined

  const handleStatusSelect = (newStatus: DatastructureStatusType) => {
    if (newStatus === DATASTRUCTURE_STATUS_TYPES.DRAFT && isInUseByReleased) {
      setIsDraftBlockedModalOpen(true)
      return
    }
    handleStatusChange(newStatus)
  }

  // isValid must be read on every render, otherwise RHF's validation does not run
  const { isValid } = form.formState
  const isConfirmButtonDisabled = !hasUserChanges || !isValid || isLoading

  const renderTabContent = () => {
    switch (subTabValue) {
      case 'structure':
        return (
          <StructureDefinitionTab
            isReadOnly={isReadOnly}
            modelSessionManager={modelSessionManager}
            isAvailable={isVersionAvailable}
          />
        )
      case 'versionInfo':
      default:
        return <VersionInfoTab form={form} isReadOnly={isReadOnly} isAvailable={isVersionAvailable} />
    }
  }

  const PageEditButtons = (
    <PageEditControls<DatastructureStatusType>
      isInUseByReleased={isInUseByReleased}
      statusProps={{
        status: statusWatch,
        onStatusChange: handleStatusSelect,
        statusOptions: Object.values(DATASTRUCTURE_STATUS_TYPES),
        canStage,
        canRelease,
        canSetDraft,
        statusHint,
        availableHint: !canRelease ? tCommon('messages.releasePermissionRequiredHint') : undefined,
      }}
      confirmButtonType="button"
      onConfirmClick={handleSave}
      isConfirmButtonDisabled={isConfirmButtonDisabled}
      isCancelButtonDisabled={isLoading}
      onCancelClick={handleExitButtonClick}
      hasCard={false}
      canEdit={canEdit}
      isReadOnly={isReadOnly}
      onEditClick={() => updateMode(true)}
      cancelButtonTitle={tCommon('actions.exit')}
      wrapperClassname="w-auto"
    />
  )

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
        customElement={PageEditButtons}
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

      <InfoModal
        open={isDraftBlockedModalOpen}
        title={tCommon('draftBlockedModal.title')}
        description={tCommon('draftBlockedModal.description')}
        onOpenChange={setIsDraftBlockedModalOpen}
        onClose={() => setIsDraftBlockedModalOpen(false)}
      />
    </PageContainer>
  )
}
