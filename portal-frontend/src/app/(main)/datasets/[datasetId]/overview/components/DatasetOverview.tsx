'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { FormEvent, useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import {
  usePatchDataset,
  useReleaseDataset,
  useStageDataset,
  useUnreleaseDataset,
  useUnstageDataset,
  useUpdateReleasedDatasetMeta,
} from '@/app/services/api/datasets/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { FooterElement } from '@/components/form/FooterElement'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { NoDataPage } from '@/components/no-data/no-data-page/NoDataPage'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import PageEditControls from '@/components/page-edit-controls/PageEditControls'
import { PageHeader } from '@/components/page-header/PageHeader'
import { BasicTooltip } from '@/components/tooltip/Tooltip'
import { Checkbox } from '@/components/ui/checkbox'
import { Form, FormControl, FormField, FormItem, FormLabel } from '@/components/ui/form'
import { useError } from '@/hooks/use-error'
import { usePermissions } from '@/hooks/use-permissions'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { cn } from '@/lib/utils'
import { ASSIGNMENT_SCOPE_TYPES } from '@/types/assignments'
import { SelectOption } from '@/types/common'
import { PERMISSION_NAMES } from '@/types/currentUser'
import {
  CompletionStepData,
  Dataset,
  DATASET_STATUS_TYPES,
  DatasetFormAvailableSchema,
  DatasetFormDraft,
  DatasetFormDraftSchema,
  DatasetStatusTypes,
  DatasetUpdateApiData,
  DatasetUpdateApiSchema,
} from '@/types/datasets'
import { pickDirtyValues } from '@/utils/form'

import { mapDatasetToFormData } from '../../../utils/mappers'
import { BaseInfoForm } from '../../components/BaseInfoForm'
import { CompletionStep } from '../../components/CompletionStep'
import { usePipelinePermissions } from '../../data-flow/pipeline-editor/_hooks/use-pipeline-permissions'
import { ApiList } from './ApiList'
import { PipelineList } from './PipelineList'

interface DatasetOverviewProps {
  dataset: Dataset
  groupCount: number
  roleCount: number
  testId?: string
  datapoolOptions: SelectOption[]
}
export const DatasetOverview = (props: DatasetOverviewProps) => {
  const { dataset, groupCount, roleCount, testId, datapoolOptions } = props
  const t = useTranslations('datasets')
  const tCommon = useTranslations('common')

  const { hasPermission, hasScopedPermission } = usePermissions()

  const canRead = hasScopedPermission(
    PERMISSION_NAMES.DATASET_READ,
    ASSIGNMENT_SCOPE_TYPES.DATASET,
    dataset.id,
    dataset.datapool?.id,
  )

  const canUpdate = hasScopedPermission(
    PERMISSION_NAMES.DATASET_UPDATE,
    ASSIGNMENT_SCOPE_TYPES.DATASET,
    dataset.id,
    dataset.datapool?.id,
  )

  const canRelease = hasScopedPermission(
    PERMISSION_NAMES.DATASET_RELEASE,
    ASSIGNMENT_SCOPE_TYPES.DATASET,
    dataset.id,
    dataset.datapool?.id,
  )

  const canReadDatastructures = hasPermission(PERMISSION_NAMES.DATASTRUCTURE_READ)

  const { canEdit: canEditPipeline } = usePipelinePermissions(dataset.id)

  const searchParams = useSearchParams()
  const mode = searchParams.get('mode')

  const { pipelines, namedApis } = dataset
  const pipelineList = pipelines ?? []
  const namedApiList = namedApis ?? []

  const router = useRouter()
  const { handleFormValidationError } = useError()
  const [isReadOnly, setIsReadOnly] = useState(mode !== 'edit')
  const [dataSetStatus, setDataSetStatus] = useState<DatasetStatusTypes>(
    dataset.dataSetStatus ?? DATASET_STATUS_TYPES.DRAFT,
  )

  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  const updateDataset = usePatchDataset()
  const updateReleasedMeta = useUpdateReleasedDatasetMeta()
  const stageDataset = useStageDataset()
  const unstageDataset = useUnstageDataset()
  const releaseDataset = useReleaseDataset()
  const unreleaseDataset = useUnreleaseDataset()

  const isLoading =
    updateDataset.isPending ||
    updateReleasedMeta.isPending ||
    stageDataset.isPending ||
    unstageDataset.isPending ||
    releaseDataset.isPending ||
    unreleaseDataset.isPending

  const form = useForm<DatasetFormDraft>({
    resolver: zodResolver(DatasetFormDraftSchema),
    mode: 'onChange',
    defaultValues: mapDatasetToFormData(dataset),
  })

  // Reset form and local status when dataset prop changes (e.g. after router.refresh())
  useEffect(() => {
    const formData = mapDatasetToFormData(dataset)
    form.reset(formData)
    setDataSetStatus(dataset.dataSetStatus ?? DATASET_STATUS_TYPES.DRAFT)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dataset])

  const isDraftMode = dataSetStatus === DATASET_STATUS_TYPES.DRAFT || !dataSetStatus
  const hasStatusChanged = dataSetStatus !== dataset.dataSetStatus

  const hasUnsavedChanges = form.formState.isDirty || hasStatusChanged
  const hasOnlyStatusChanges = !form.formState.isDirty && hasStatusChanged

  const formValues = useWatch({ control: form.control })

  const canSetAvailable = useMemo(() => {
    const hasDistribution = !!dataset.pipelines?.length || !!dataset.namedApis?.length
    const hasAssignments = groupCount > 0 && roleCount > 0
    return DatasetFormAvailableSchema.safeParse(formValues).success && hasDistribution && hasAssignments
  }, [formValues, dataset.pipelines, dataset.namedApis, groupCount, roleCount])

  // Auto-revert status to draft when required fields become invalid
  const revalidateDraftMode = () => {
    const isReadyOrAvailable =
      dataSetStatus === DATASET_STATUS_TYPES.READY || dataSetStatus === DATASET_STATUS_TYPES.AVAILABLE
    if (isReadyOrAvailable && !canSetAvailable) {
      setDataSetStatus(DATASET_STATUS_TYPES.DRAFT)
      toast.info(tCommon('info.switchMode'))
    }
  }

  useEffect(() => {
    if (isDraftMode) {
      form.clearErrors()
    } else {
      void form.trigger()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isDraftMode])

  useEffect(() => {
    revalidateDraftMode()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canSetAvailable, dataSetStatus])

  const getTransitionSteps = (
    oldStatus: DatasetStatusTypes,
    newStatus: DatasetStatusTypes,
  ): ((id: string) => Promise<unknown>)[] => {
    if (oldStatus === newStatus) return []

    const transitions: Record<string, ((id: string) => Promise<unknown>)[]> = {
      'DRAFT->READY': [stageDataset.mutateAsync],
      'READY->DRAFT': [unstageDataset.mutateAsync],
      'READY->AVAILABLE': [releaseDataset.mutateAsync],
      'AVAILABLE->READY': [unreleaseDataset.mutateAsync],
      'DRAFT->AVAILABLE': [stageDataset.mutateAsync, releaseDataset.mutateAsync],
      'AVAILABLE->DRAFT': [unreleaseDataset.mutateAsync, unstageDataset.mutateAsync],
    }

    return transitions[`${oldStatus}->${newStatus}`] ?? []
  }

  const handleSaveAndTransition = async (formData: DatasetFormDraft) => {
    const serverStatus = dataset.dataSetStatus ?? DATASET_STATUS_TYPES.DRAFT

    try {
      // Step 1: Save form data if dirty
      if (form.formState.isDirty) {
        const { dirtyFields } = form.formState
        const valuesForValidation = { ...formData, id: dataset.id }
        const parsed =
          serverStatus === DATASET_STATUS_TYPES.DRAFT
            ? DatasetUpdateApiSchema.safeParse(valuesForValidation)
            : DatasetFormAvailableSchema.safeParse(valuesForValidation)

        if (!parsed.success) {
          handleFormValidationError(parsed.error)
          return false
        }

        const fieldsToUpdate = pickDirtyValues(parsed.data, dirtyFields)
        const updateData: DatasetUpdateApiData = {
          id: dataset.id,
          name: formData.name,
          ...fieldsToUpdate,
        }

        if (serverStatus === DATASET_STATUS_TYPES.DRAFT) {
          await updateDataset.mutateAsync(updateData)
        } else {
          await updateReleasedMeta.mutateAsync({ ...parsed.data, id: dataset.id })
        }
      }

      // Step 2: Execute status transitions
      const steps = getTransitionSteps(serverStatus, dataSetStatus)
      for (const step of steps) {
        await step(dataset.id)
      }

      // Success message
      if (steps.length > 0) {
        const messageMap: Record<string, string> = {
          'DRAFT->READY': t('messages.stageSuccess'),
          'READY->DRAFT': t('messages.unstageSuccess'),
          'READY->AVAILABLE': t('messages.releaseSuccess'),
          'AVAILABLE->READY': t('messages.unreleaseSuccess'),
          'DRAFT->AVAILABLE': t('messages.releaseSuccess'),
          'AVAILABLE->DRAFT': t('messages.unstageSuccess'),
        }
        toast.success(messageMap[`${serverStatus}->${dataSetStatus}`] ?? t('messages.updateSuccess'))
      } else {
        toast.success(t('messages.updateSuccess'))
      }

      router.refresh()
      setIsReadOnly(true)
      return true
    } catch {
      toast.error(t('messages.transitionError'))
      return false
    }
  }

  const handleSave = async (): Promise<boolean> => {
    // If only status changed (form not dirty), call transition directly
    let isSaved = false
    if (hasOnlyStatusChanges) {
      isSaved = await handleSaveAndTransition(form.getValues() as DatasetFormDraft)
    } else {
      await form.handleSubmit(async data => {
        isSaved = await handleSaveAndTransition(data as DatasetFormDraft)
      })()
    }

    return isSaved
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    void handleSave()
  }

  const handleStatusChange = (newStatus: DatasetStatusTypes) => {
    setDataSetStatus(newStatus)
  }

  const handleExit = () => {
    if (hasUnsavedChanges) {
      setIsExitModalOpen(true)
    } else {
      setIsReadOnly(true)
      setDataSetStatus(dataset.dataSetStatus)
    }
  }

  const handleDiscardAndExit = () => {
    setIsExitModalOpen(false)
    form.reset(mapDatasetToFormData(dataset))
    setDataSetStatus(dataset.dataSetStatus)
    setIsReadOnly(true)
  }

  const handleSaveAndExit = () => {
    // Handle status-only change (form not dirty)
    if (hasOnlyStatusChanges) {
      void handleSaveAndTransition(form.getValues() as DatasetFormDraft).then(() => {
        setIsExitModalOpen(false)
      })
      return
    }

    void form.handleSubmit(data => {
      void handleSaveAndTransition(data as DatasetFormDraft).then(() => {
        setIsExitModalOpen(false)
      })
    })()
  }

  useRegisterUnsavedChanges(hasUnsavedChanges, handleSave)

  const getList = (title: string, items: string[]) => (
    <div className="w-[50%] grid grid-cols-2 mt-2">
      <h4>{title}</h4>
      <ul className="list-none">
        {items.map(item => (
          <li key={item} className="font-normal text-sm">
            {item}
          </li>
        ))}
      </ul>
    </div>
  )

  const completionSteps: CompletionStepData[] = [
    {
      title: t('overview.completion.dataFlow.title'),
      description: t('overview.completion.dataFlow.description'),
      isCompleted: pipelineList.length > 0 || namedApiList.length > 0,
      buttons: [],
      content: (
        <>
          <PipelineList datasetId={dataset.id} pipelines={pipelineList} canEditPipeline={canEditPipeline} />
          <div className="border-t" />
          <ApiList
            datasetId={dataset.id}
            apis={namedApiList}
            canEdit={canUpdate}
            canView={canRead && canReadDatastructures}
            isOpenDataAccess={dataset.openDataAccess}
          />
        </>
      ),
    },
    {
      title: t('overview.completion.accessManagement.title'),
      isCompleted: groupCount > 0 && roleCount > 0,
      buttons: [
        {
          text: isReadOnly
            ? t('overview.completion.accessManagement.button.readOnly')
            : t('overview.completion.accessManagement.button.editable'),
          routeParam: 'access-management',
        },
      ],
      content: (
        <>
          {groupCount > 0 || roleCount > 0 ? (
            <>
              {groupCount > 0 && getList(t('overview.completion.accessManagement.groups'), [String(groupCount)])}
              {roleCount > 0 && getList(t('overview.completion.accessManagement.roles'), [String(roleCount)])}
            </>
          ) : (
            <div>{t('overview.completion.accessManagement.noAssignments')}</div>
          )}
          <div className="mt-6 pt-4 border-t">
            <FormField
              control={form.control}
              name="openDataAccess"
              render={({ field }) => (
                <BasicTooltip
                  className="bg-primary text-primary-foreground rounded-md p-2 text-sm max-w-xs"
                  tooltipContent={<div>{t('overview.completion.accessManagement.openDataAccessHint')}</div>}
                >
                  <FormItem className="w-[50%] grid grid-cols-2 gap-0 mt-2">
                    <FormLabel className="font-semibold text-base">
                      {t('overview.completion.accessManagement.openDataAccess')}
                    </FormLabel>
                    <FormControl>
                      <Checkbox checked={field.value} onCheckedChange={field.onChange} disabled={isReadOnly} />
                    </FormControl>
                  </FormItem>
                </BasicTooltip>
              )}
            />
          </div>
        </>
      ),
    },
  ]

  const customElementEditMode = (
    <PageEditControls<DatasetStatusTypes>
      status={dataSetStatus}
      onStatusChange={handleStatusChange}
      statusOptions={Object.values(DATASET_STATUS_TYPES)}
      canSetAvailable={canSetAvailable}
      canRelease={canRelease}
      confirmButtonType="submit"
      formId="dataset-form"
      isConfirmButtonDisabled={!hasUnsavedChanges || isLoading}
      onCancelClick={() => {
        handleExit()
      }}
      hasCard={false}
      isReadOnly={isReadOnly}
      onEditClick={() => setIsReadOnly(false)}
      canEdit={canUpdate}
      cancelButtonTitle={tCommon('actions.exit')}
    />
  )

  if (!dataset) {
    return <NoDataPage title={tCommon('noData')} />
  }

  return (
    <PageContainer testId={testId} headerType="onlyTitle" className="overflow-auto">
      <PageHeader title={dataset.name} customElement={customElementEditMode} />

      <PageBackground className="overflow-y-auto" hasBackground={!isReadOnly}>
        <Form {...form}>
          <form
            id="dataset-form"
            data-testid="datasetBaseInfoForm"
            aria-label={`${tCommon('form')} ${t('overview.info.title')}`}
            onSubmit={handleSubmit}
            className="h-full"
          >
            <ContentCard className={cn('h-auto')} footerElement={<FooterElement />}>
              <BaseInfoForm
                form={form}
                isReadOnly={isReadOnly}
                isLoading={isLoading}
                datapoolOptions={datapoolOptions}
              />
            </ContentCard>

            <div className="mt-6">
              {completionSteps.map(step => (
                <ContentCard key={step.title} className="w-full, h-auto mb-6">
                  <CompletionStep step={step} datasetId={dataset.id} />
                </ContentCard>
              ))}
            </div>
          </form>
        </Form>
      </PageBackground>

      <ExitWarningModal
        open={isExitModalOpen}
        onDiscard={handleDiscardAndExit}
        onConfirm={handleSaveAndExit}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
