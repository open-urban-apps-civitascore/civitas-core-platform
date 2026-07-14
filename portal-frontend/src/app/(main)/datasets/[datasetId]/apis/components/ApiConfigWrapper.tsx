'use client'

import { useTranslations } from 'next-intl'
import { FormEvent } from 'react'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Button } from '@/components/ui/button'
import { usePermissions } from '@/hooks/use-permissions'
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { Dataset } from '@/types/datasets'

export type ApiConfigTab = 'basicInfo' | 'layer' | 'styles'

interface ApiConfigWrapperProps {
  dataset: Dataset
  isReadOnly: boolean
  hasUnsavedChanges: boolean
  isFormValid: boolean
  isLoading: boolean
  tabs: Tab<ApiConfigTab>[]
  selectedTab: ApiConfigTab
  onTabChange: (tab: ApiConfigTab) => void
  completedTabs: ApiConfigTab[]
  typeLabel: string
  testId?: string
  isExitModalOpen: boolean
  onExitModalOpenChange: (open: boolean) => void
  onEdit: () => void
  onExit: () => void
  onDiscard: () => void
  onSaveAndExit: () => void
  onSubmit: (e: FormEvent) => void
  children: React.ReactNode
}

export const ApiConfigWrapper = (props: ApiConfigWrapperProps) => {
  const {
    dataset,
    isReadOnly,
    hasUnsavedChanges,
    isFormValid,
    isLoading,
    tabs,
    selectedTab,
    onTabChange,
    completedTabs,
    typeLabel,
    testId,
    isExitModalOpen,
    onExitModalOpenChange,
    onEdit,
    onExit,
    onDiscard,
    onSaveAndExit,
    onSubmit,
    children,
  } = props

  const tCommon = useTranslations('common')
  const t = useTranslations('datasets.overview.completion.apis.config')

  const { hasScopedPermission, hasPermission } = usePermissions()
  const canUpdateDataset = hasScopedPermission(
    PERMISSION_NAMES.DATASET_UPDATE,
    ASSIGNMENT_SCOPE_TYPES.DATASET,
    dataset.id,
    dataset.datapool?.id,
  )

  const canReadDatastructures = hasPermission(PERMISSION_NAMES.DATASTRUCTURE_READ)

  const canEdit = canUpdateDataset && canReadDatastructures

  const buttonGroup = isReadOnly ? (
    <div className="flex items-center gap-2 px-[var(--layout-padding)]">
      <Button data-testid="editButton" type="button" onClick={onEdit}>
        {tCommon('actions.edit')}
      </Button>
    </div>
  ) : (
    <ActionButtons
      hasCard={false}
      wrapperClassname="px-[var(--layout-padding)]"
      confirmButtonType="submit"
      formId="api-config-form"
      onCancelClick={onExit}
      isCancelButtonDisabled={isLoading}
      isConfirmButtonDisabled={!hasUnsavedChanges || !isFormValid || isLoading}
      cancelButtonTitle={tCommon('actions.exit')}
      confirmButtonTitle={tCommon('actions.submit')}
    />
  )

  return (
    <PageContainer testId={testId} headerType="withSubTabsOrSubtitle" className="overflow-auto">
      <PageHeader
        title={typeLabel}
        badgeTitle={t('protectedBadge')}
        customElement={canEdit ? buttonGroup : undefined}
        segmentedControlBarProps={{
          tabs,
          selectedTab,
          onTabChange,
          completedTabs,
          tabsWithNoCompletionStatus: ['styles'],
          hasCompletionStatus: true,
        }}
      />
      <PageBackground className="overflow-y-auto" hasBackground={!isReadOnly}>
        {isLoading ? (
          <LoadingSpinner className="h-full" />
        ) : (
          <form
            id="api-config-form"
            data-testid="apiConfigForm"
            aria-label={`${tCommon('form')} ${typeLabel}`}
            onSubmit={onSubmit}
            className="h-full"
          >
            {children}
          </form>
        )}
      </PageBackground>
      <ExitWarningModal
        open={isExitModalOpen}
        onOpenChange={onExitModalOpenChange}
        onDiscard={onDiscard}
        onConfirm={onSaveAndExit}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
