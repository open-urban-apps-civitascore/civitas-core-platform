'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { AxiosError } from 'axios'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import {
  usePatchDataset,
  usePublishDataset,
  useReleaseDataset,
  useUnpublishDataset,
  useUnreleaseDataset,
  useUpdatePublishedDatasetMeta,
} from '@/app/services/api/datasets/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { FooterElement } from '@/components/form/FooterElement'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import PageEditControls from '@/components/page-edit-controls/PageEditControls'
import { PageHeader } from '@/components/page-header/PageHeader'
import { BasicTooltip } from '@/components/tooltip/Tooltip'
import { Checkbox } from '@/components/ui/checkbox'
import { Form, FormControl, FormField, FormItem, FormLabel } from '@/components/ui/form'
import { useError } from '@/hooks/use-error'
import { cn } from '@/lib/utils'
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
import { isNameConflictError } from '@/utils/errors'
import { pickDirtyValues } from '@/utils/form'

import { mapDatasetToFormData } from '../../../utils/mappers'
import { BaseInfoForm } from '../../components/BaseInfoForm'
import { CompletionStep } from '../../components/CompletionStep'

interface DatasetOverviewProps {
  dataset: Dataset
  groupCount: number
  roleCount: number
  testId?: string
}
export const DatasetOverview = (props: DatasetOverviewProps) => {
  const { dataset, groupCount, roleCount, testId } = props
  const t = useTranslations('datasets')
  const tCommon = useTranslations('common')

  const searchParams = useSearchParams()
  const mode = searchParams.get('mode')

  const { pipelines, distributions } = dataset
  const pipelineNames = pipelines?.map(pipeline => pipeline.name) || []
  const distributionAccessURL = distributions?.map(distribution => distribution.accessUrl) || []

  const router = useRouter()
  const { handleFormValidationError, handleNameError } = useError()
  const [isReadOnly, setIsReadOnly] = useState(mode !== 'edit')
  const [dataSetStatus, setDataSetStatus] = useState<DatasetStatusTypes>(
    dataset.dataSetStatus ?? DATASET_STATUS_TYPES.DRAFT,
  )

  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  const updateDataset = usePatchDataset()
  const updatePublishedMeta = useUpdatePublishedDatasetMeta()
  const publishDataset = usePublishDataset()
  const unpublishDataset = useUnpublishDataset()
  const releaseDataset = useReleaseDataset()
  const unreleaseDataset = useUnreleaseDataset()

  const isLoading =
    updateDataset.isPending ||
    updatePublishedMeta.isPending ||
    publishDataset.isPending ||
    unpublishDataset.isPending ||
    releaseDataset.isPending ||
    unreleaseDataset.isPending

  const form = useForm<DatasetFormDraft>({
    resolver: zodResolver(DatasetFormDraftSchema),
    mode: 'onChange',
    defaultValues: mapDatasetToFormData(dataset),
  })

  // Reset form and local status when dataset prop changes (e.g. after router.refresh())
  useEffect(() => {
    form.reset(mapDatasetToFormData(dataset))
    setDataSetStatus(dataset.dataSetStatus ?? DATASET_STATUS_TYPES.DRAFT)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dataset])

  const isDraftMode = dataSetStatus === DATASET_STATUS_TYPES.DRAFT || !dataSetStatus
  const hasStatusChanged = dataSetStatus !== dataset.dataSetStatus

  const formValues = useWatch({ control: form.control })

  const canSetAvailable = useMemo(() => {
    const hasDistribution = !!dataset.pipelines?.length || !!dataset.distributions?.length
    const hasAssignments = groupCount > 0 && roleCount > 0
    return DatasetFormAvailableSchema.safeParse(formValues).success && hasDistribution && hasAssignments
  }, [formValues, dataset.pipelines, dataset.distributions, groupCount, roleCount])

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
      'DRAFT->READY': [publishDataset.mutateAsync],
      'READY->DRAFT': [unpublishDataset.mutateAsync],
      'READY->AVAILABLE': [releaseDataset.mutateAsync],
      'AVAILABLE->READY': [unreleaseDataset.mutateAsync],
      'DRAFT->AVAILABLE': [publishDataset.mutateAsync, releaseDataset.mutateAsync],
      'AVAILABLE->DRAFT': [unreleaseDataset.mutateAsync, unpublishDataset.mutateAsync],
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
          return
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
          await updatePublishedMeta.mutateAsync({ ...parsed.data, id: dataset.id })
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
          'DRAFT->READY': t('messages.publishSuccess'),
          'READY->DRAFT': t('messages.unpublishSuccess'),
          'READY->AVAILABLE': t('messages.releaseSuccess'),
          'AVAILABLE->READY': t('messages.unreleaseSuccess'),
          'DRAFT->AVAILABLE': t('messages.releaseSuccess'),
          'AVAILABLE->DRAFT': t('messages.unpublishSuccess'),
        }
        toast.success(messageMap[`${serverStatus}->${dataSetStatus}`] ?? t('messages.updateSuccess'))
      } else {
        toast.success(t('messages.updateSuccess'))
      }

      router.refresh()
      setIsReadOnly(true)
    } catch (error) {
      if (isNameConflictError(error as AxiosError)) {
        handleNameError(form, form.getValues('name'))
      } else {
        toast.error(t('messages.transitionError'))
      }
    }
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()

    // If only status changed (form not dirty), call transition directly
    if (!form.formState.isDirty && hasStatusChanged) {
      void handleSaveAndTransition(form.getValues() as DatasetFormDraft)
      return
    }

    void form.handleSubmit(data => {
      void handleSaveAndTransition(data as DatasetFormDraft)
    })(e)
  }

  const handleStatusChange = (newStatus: DatasetStatusTypes) => {
    setDataSetStatus(newStatus)
  }

  const handleExit = () => {
    if (form.formState.isDirty || hasStatusChanged) {
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
    if (!form.formState.isDirty && hasStatusChanged) {
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
      isCompleted: pipelineNames?.length > 0 || distributionAccessURL?.length > 0,
      buttons: [{ text: t('overview.completion.dataFlow.button'), routeParam: 'data-flow' }],
      content:
        pipelineNames?.length > 0 || distributionAccessURL?.length > 0 ? (
          <>
            {pipelineNames?.length > 0 && getList(t('overview.completion.dataFlow.pipelines'), pipelineNames)}
            {distributionAccessURL?.length > 0 &&
              getList(t('overview.completion.dataFlow.distributionAccessURLs'), distributionAccessURL)}
          </>
        ) : (
          <div>{t('overview.completion.dataFlow.noDataFlow')}</div>
        ),
    },
    {
      title: t('overview.completion.accessManagement.title'),
      isCompleted: groupCount > 0 && roleCount > 0,
      buttons: [{ text: t('overview.completion.accessManagement.button'), routeParam: 'access-management' }],
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
      confirmButtonType="submit"
      formId="dataset-form"
      isConfirmButtonDisabled={(!form.formState.isDirty && !hasStatusChanged) || isLoading}
      onCancelClick={() => {
        handleExit()
      }}
      hasCard={false}
      isReadOnly={isReadOnly}
      onEditClick={() => setIsReadOnly(false)}
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
              <BaseInfoForm form={form} isReadOnly={isReadOnly} isLoading={isLoading} />
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
