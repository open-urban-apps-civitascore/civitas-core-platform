'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { FormEvent, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { useCreateVisualization, useUpdateVisualization } from '@/app/services/api/visualizations/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { usePermissions } from '@/hooks/use-permissions'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { Dataset, DATASET_STATUS_TYPES } from '@/types/datasets'
import {
  Visualization,
  VisualizationFormData,
  visualizationFormSchema,
  VisualizationTypeQuery,
} from '@/types/visualizations'

import { DashboardOptionsForm } from './DashboardOptionsForm'
import { VisualizationConfigForm } from './VisualizationConfigForm'

type VisualizationConfigTab = 'basicInfo' | 'options'

interface VisualizationConfigPageProps {
  dataset: Dataset
  visualizationType?: VisualizationTypeQuery
  existingVisualization?: Visualization
  testId?: string
}

export const VisualizationConfigPage = ({
  dataset,
  visualizationType,
  existingVisualization,
  testId,
}: VisualizationConfigPageProps) => {
  const t = useTranslations('datasets.overview.completion.visualizations.config')
  const tCommon = useTranslations('common')

  const isCreate = !existingVisualization
  const resolvedType = visualizationType ?? existingVisualization!.type

  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const [selectedTab, setSelectedTab] = useState<VisualizationConfigTab>('basicInfo')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isReadOnly, setIsReadOnly] = useState(isCreate ? false : searchParams.get('mode') !== 'edit')
  const createVisualization = useCreateVisualization()
  const updateVisualization = useUpdateVisualization()

  const { hasScopedPermission } = usePermissions()
  const canEdit = hasScopedPermission(
    PERMISSION_NAMES.DATASET_UPDATE,
    ASSIGNMENT_SCOPE_TYPES.DATASET,
    dataset.id,
    dataset.datapool?.id,
  )
  const canModify = canEdit && (!isCreate || dataset.dataSetStatus === DATASET_STATUS_TYPES.DRAFT)
  const isFormReadOnly = isReadOnly || !canModify

  const defaultValues: VisualizationFormData = {
    name: existingVisualization?.name ?? '',
    description: existingVisualization?.description ?? '',
    standaloneMode: existingVisualization?.standaloneMode ?? 1,
    showFilters: existingVisualization?.showFilters ?? true,
    filterExpansion: existingVisualization?.filterExpansion ?? 'DEFAULT',
  }

  const form = useForm<VisualizationFormData>({
    resolver: zodResolver(visualizationFormSchema),
    mode: 'onChange',
    defaultValues,
  })

  const hasUnsavedChanges = form.formState.isDirty
  const isFormValid = form.formState.isValid

  const pageTitle = existingVisualization?.name ?? t(`title.${resolvedType}`)
  const typeLabel = t(`type.${resolvedType}`)

  const tabs: Tab<VisualizationConfigTab>[] = useMemo(
    () => [
      { value: 'basicInfo', label: 'datasets.overview.completion.visualizations.config.tabs.basicInfo' },
      ...(!isCreate
        ? [{ value: 'options' as const, label: 'datasets.overview.completion.visualizations.config.tabs.options' }]
        : []),
    ],
    [isCreate],
  )

  const completedTabs: VisualizationConfigTab[] = isFormValid ? ['basicInfo'] : []

  const badgeTitle = dataset.openDataAccess ? t('openDataBadge') : t('protectedBadge')

  const updateMode = (isEditing: boolean) => {
    if (isEditing && !canModify) return
    setIsReadOnly(!isEditing)
    const params = new URLSearchParams(searchParams.toString())
    if (isEditing) {
      params.set('mode', 'edit')
    } else {
      params.delete('mode')
    }
    const query = params.toString()
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false })
  }

  const handleExit = () => {
    if (hasUnsavedChanges) {
      setIsExitModalOpen(true)
      return
    }
    if (isCreate) {
      router.push(`/datasets/${dataset.id}`)
    } else {
      form.reset(defaultValues)
      updateMode(false)
    }
  }

  const handleDiscardAndExit = () => {
    setIsExitModalOpen(false)
    if (isCreate) {
      router.push(`/datasets/${dataset.id}`)
      return
    }
    form.reset(defaultValues)
    updateMode(false)
  }

  const handleSave = async (): Promise<boolean> => {
    if (!canModify) return false
    let isSaved = false
    await form.handleSubmit(async data => {
      try {
        if (isCreate) {
          await createVisualization.mutateAsync({ datasetId: dataset.id, visualization: data })
          toast.success(t('messages.createSuccess'))
          router.push(`/datasets/${dataset.id}`)
        } else {
          await updateVisualization.mutateAsync({
            datasetId: dataset.id,
            visualizationId: existingVisualization.id,
            visualization: data,
          })
          toast.success(t('messages.updateSuccess'))
          form.reset(data)
          updateMode(false)
          router.refresh()
        }
        isSaved = true
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

  const buttonGroup = isFormReadOnly ? (
    <Button data-testid="editButton" type="button" onClick={() => updateMode(true)}>
      {tCommon('actions.edit')}
    </Button>
  ) : (
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
        customElement={canModify ? buttonGroup : undefined}
        segmentedControlBarProps={{
          tabs,
          selectedTab,
          onTabChange: setSelectedTab,
          completedTabs,
          tabsWithNoCompletionStatus: ['options'],
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
              {selectedTab === 'basicInfo' && (
                <VisualizationConfigForm form={form} typeLabel={typeLabel} isReadOnly={isFormReadOnly} />
              )}
              {selectedTab === 'options' && !isCreate && (
                <DashboardOptionsForm form={form} isReadOnly={isFormReadOnly} />
              )}
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
