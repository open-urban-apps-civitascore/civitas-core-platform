'use client'

import { useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useState } from 'react'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { StatusDropdown } from '@/components/status-dropdown/StatusDropdown'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/use-query-params'
import {
  DATASTRUCTURE_STATUS_TYPES,
  DatastructureStatusTypes,
  DatastructureVersion,
  DatastructureVersionTab,
} from '@/types/datastructures'

import { useDatastructureVersion } from '../hooks/useDatastructureVersion'
import { StructureDefinitionTab } from './structure-definition-tab/StructureDefinitionTab'
import { VersionInfoTab } from './version-info-tab/VersionInfoTab'

const tabs: Tab<DatastructureVersionTab>[] = [
  {
    value: 'structure',
    label: 'datastructureVersions.tabs.structure',
  },
  {
    value: 'versionInfo',
    label: 'datastructureVersions.tabs.versionInfo',
  },
]

const DEFAULT_TAB = tabs[0]

interface VersionOverviewProps {
  title: string
  datastructureId: string
  version: DatastructureVersion | null
  isCreateMode: boolean
  testId: string
  otherVersions: { id: string; version: string; dataStructureVersionStatus: DatastructureStatusTypes }[]
  isDatastructureAvailable: boolean
}

export const VersionOverview = (props: VersionOverviewProps) => {
  const { title, datastructureId, version, isCreateMode, testId, otherVersions, isDatastructureAvailable } = props

  const params = useSearchParams()
  const mode = params.get('mode')
  const tCommon = useTranslations('common')
  const { setSubTabValueParam, subTabValue } = useQueryParams()

  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isReadOnly, setIsReadOnly] = useState(mode !== 'edit')

  const {
    activeSessionId,
    canSetAvailable,
    canSetDraft,
    completedTabs,
    form,
    initialFormValues,
    initialSession,
    isConfirmButtonDisabled,
    isInUse,
    isLoading,
    modelSessionManager,
    saveDatastructureVersion,
    statusHint,
    statusWatch,
    versionAlreadyExistsError,
    handleStatusChange,
  } = useDatastructureVersion({
    datastructureId,
    version,
    isCreateMode,
    otherVersions,
    isDatastructureAvailable,
    subTabValue,
  })

  const handleSave = async () => {
    const isSaved = await saveDatastructureVersion()
    if (isSaved) {
      setIsExitModalOpen(false)
    }
  }

  const handleExit = () => {
    if (activeSessionId) {
      modelSessionManager.setSession(activeSessionId, initialSession)
    }
    form.reset(initialFormValues)
    setIsReadOnly(true)
    setIsExitModalOpen(false)
  }

  const handleExitButtonClick = () => {
    if (form.formState.isDirty) setIsExitModalOpen(true)
    else handleExit()
  }

  const ActionButtonsAndStatusSwitch = (
    <div className="flex gap-6">
      <StatusDropdown
        statusOptions={Object.values(DATASTRUCTURE_STATUS_TYPES)}
        status={statusWatch}
        onStatusChange={handleStatusChange}
        canSetAvailable={canSetAvailable}
        canSetDraft={canSetDraft}
        statusHint={statusHint}
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
      case 'versionInfo':
        return (
          <VersionInfoTab form={form} isReadOnly={isReadOnly} versionAlreadyExistsError={versionAlreadyExistsError} />
        )
      case 'structure':
      default:
        return (
          <StructureDefinitionTab isReadOnly={isReadOnly} modelSessionManager={modelSessionManager} isInUse={isInUse} />
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
        customElement={isReadOnly ? EditButton : ActionButtonsAndStatusSwitch}
      />
      {isLoading ? <LoadingSpinner className="h-full" /> : renderTabContent()}

      <ExitWarningModal
        open={isExitModalOpen}
        onOpenChange={() => setIsExitModalOpen(false)}
        onDiscard={handleExit}
        onConfirm={handleSave}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
