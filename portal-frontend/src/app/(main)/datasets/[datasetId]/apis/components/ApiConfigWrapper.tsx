'use client'

import { useTranslations } from 'next-intl'
import { FormEvent } from 'react'

import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Button } from '@/components/ui/button'

export type ApiConfigTab = 'basicInfo' | 'layer' | 'styles'

interface ApiConfigWrapperProps {
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

  const buttonGroup = isReadOnly ? (
    <div className="flex items-center gap-2 px-[var(--layout-padding)]">
      <Button data-testid="editButton" type="button" onClick={onEdit}>
        {tCommon('actions.edit')}
      </Button>
    </div>
  ) : (
    <div className="flex items-center gap-2 px-[var(--layout-padding)]">
      <Button data-testid="cancelButton" type="button" variant="secondary" onClick={onExit} disabled={isLoading}>
        {tCommon('actions.exit')}
      </Button>
      <Button
        data-testid="confirmButton"
        type="submit"
        form="api-config-form"
        disabled={!hasUnsavedChanges || !isFormValid || isLoading}
      >
        {tCommon('actions.submit')}
      </Button>
    </div>
  )

  return (
    <PageContainer testId={testId} headerType="withSubTabsOrSubtitle" className="overflow-auto">
      <PageHeader
        title={typeLabel}
        badgeTitle={t('protectedBadge')}
        customElement={buttonGroup}
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
        <form
          id="api-config-form"
          data-testid="apiConfigForm"
          aria-label={`${tCommon('form')} ${typeLabel}`}
          onSubmit={onSubmit}
          className="h-full"
        >
          {children}
        </form>
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
