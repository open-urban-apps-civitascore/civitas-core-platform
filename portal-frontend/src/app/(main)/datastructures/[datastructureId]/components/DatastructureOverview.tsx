'use client'

import { useTranslations } from 'next-intl'
import { useState } from 'react'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { StatusDropdown } from '@/components/status-dropdown/StatusDropdown'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { Datastructure, DATASTRUCTURE_STATUS_TYPES, DatastructureTab } from '@/types/datastructures'
import { mapDatastructureVersionsApiToListData } from '@/utils/datastructures'

import { useDatastructure } from '../hooks/useDatastructure'
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
    value: 'accessPermissions',
    label: 'datastructures.tabs.accessPermissions',
  },
]

const disabledTabs: DatastructureTab[] = ['accessPermissions']

interface DatastructureOverviewProps {
  datastructure: Datastructure
}

export const DatastructureOverview = (props: DatastructureOverviewProps) => {
  const { datastructure } = props
  const t = useTranslations('datastructures')
  const tCommon = useTranslations('common')

  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isReadOnly, setIsReadOnly] = useState(true)
  const {
    canSetAvailable,
    canSetDraft,
    completedTabs,
    form,
    handleStatusChange,
    isConfirmButtonDisabled,
    isLoading,
    resetToInitialState,
    saveDatastructure,
    selectedTab,
    setSelectedTab,
    statusHint,
    statusWatch,
  } = useDatastructure({ datastructure })

  const handleSave = async () => {
    const isSaved = await saveDatastructure()
    if (isSaved) {
      setIsExitModalOpen(false)
    }
  }

  const handleExit = () => {
    resetToInitialState()
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
    switch (selectedTab) {
      case 'basicInfo':
        return <BasicInfoTab form={form} isReadOnly={isReadOnly} />
      case 'versions':
        return (
          <VersionsTab
            datastructureId={datastructure.id}
            versions={mapDatastructureVersionsApiToListData(datastructure.dataStructureVersions)}
            rowCount={datastructure.dataStructureVersions.length}
            isReadOnly={isReadOnly}
            isDirty={form.formState.isDirty}
            isLoading={isLoading}
            onSave={saveDatastructure}
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
          tabs: tabs,
          selectedTab: selectedTab,
          onTabChange: setSelectedTab,
          completedTabs,
          disabledTabs,
          hasCompletionStatus: true,
        }}
        customElement={isReadOnly ? EditButton : ActionButtonsAndStatusSwitch}
      />
      <PageBackground className="overflow-y-auto" hasBackground={!isReadOnly}>
        <Form {...form}>
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
        onOpenChange={() => setIsExitModalOpen(false)}
        onDiscard={handleExit}
        onConfirm={handleSave}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
