'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { FormEvent, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Form } from '@/components/ui/form'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { Dataset } from '@/types/datasets'
import { VisualizationFormData, visualizationFormSchema, VisualizationTypeQuery } from '@/types/visualizations'

import { VisualizationConfigForm } from './VisualizationConfigForm'

type VisualizationConfigTab = 'basicInfo'

interface VisualizationConfigPageProps {
  dataset: Dataset
  visualizationType: VisualizationTypeQuery
  testId?: string
}

export const VisualizationConfigPage = ({ dataset, visualizationType, testId }: VisualizationConfigPageProps) => {
  const t = useTranslations('datasets.overview.completion.visualizations.config')
  const tCommon = useTranslations('common')

  const router = useRouter()
  const [selectedTab, setSelectedTab] = useState<VisualizationConfigTab>('basicInfo')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  const form = useForm<VisualizationFormData>({
    resolver: zodResolver(visualizationFormSchema),
    mode: 'onChange',
    defaultValues: {
      name: '',
      description: '',
    },
  })

  const hasUnsavedChanges = form.formState.isDirty
  const isFormValid = form.formState.isValid

  const pageTitle = t(`title.${visualizationType}`)
  const typeLabel = t(`type.${visualizationType}`)

  const tabs: Tab<VisualizationConfigTab>[] = useMemo(
    () => [{ value: 'basicInfo', label: 'datasets.overview.completion.visualizations.config.tabs.basicInfo' }],
    [],
  )

  const completedTabs: VisualizationConfigTab[] = isFormValid ? ['basicInfo'] : []

  const badgeTitle = dataset.openDataAccess ? t('openDataBadge') : t('protectedBadge')

  const handleExit = () => {
    if (hasUnsavedChanges) {
      setIsExitModalOpen(true)
      return
    }
    router.push(`/datasets/${dataset.id}`)
  }

  const handleDiscardAndExit = () => {
    setIsExitModalOpen(false)
    router.push(`/datasets/${dataset.id}`)
  }

  const handleSave = async (): Promise<boolean> => {
    let isSaved = false
    await form.handleSubmit(async _data => {
      try {
        // TODO: call createVisualization mutation when API is available
        toast.error(t('messages.saveError'))
        isSaved = false
      } catch {
        toast.error(t('messages.saveError'))
        isSaved = false
      }
    })()
    return isSaved
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    void handleSave()
  }

  const handleSaveAndExit = () => {
    void handleSave().then(isSaved => {
      if (isSaved) setIsExitModalOpen(false)
    })
  }

  useRegisterUnsavedChanges(hasUnsavedChanges, handleSave)

  const buttonGroup = (
    <ActionButtons
      confirmButtonType="submit"
      formId="visualization-config-form"
      onCancelClick={handleExit}
      isConfirmButtonDisabled={!hasUnsavedChanges || !isFormValid}
      hasCard={false}
      wrapperClassname="px-[var(--layout-padding)]"
    />
  )

  return (
    <PageContainer testId={testId} headerType="withSubTabsOrSubtitle" className="overflow-auto">
      <PageHeader
        title={pageTitle}
        badgeTitle={badgeTitle}
        customElement={buttonGroup}
        segmentedControlBarProps={{
          tabs,
          selectedTab,
          onTabChange: setSelectedTab,
          completedTabs,
          hasCompletionStatus: true,
        }}
      />

      <PageBackground className="overflow-y-auto" hasBackground>
        <Form {...form}>
          <form
            id="visualization-config-form"
            data-testid="visualizationConfigForm"
            aria-label={`${tCommon('form')} ${pageTitle}`}
            onSubmit={handleSubmit}
            className="h-full"
          >
            <ContentCard>
              {selectedTab === 'basicInfo' && <VisualizationConfigForm form={form} typeLabel={typeLabel} />}
            </ContentCard>
          </form>
        </Form>
      </PageBackground>

      <ExitWarningModal
        open={isExitModalOpen}
        onOpenChange={setIsExitModalOpen}
        onDiscard={handleDiscardAndExit}
        onConfirm={handleSaveAndExit}
        isLoading={false}
      />
    </PageContainer>
  )
}
