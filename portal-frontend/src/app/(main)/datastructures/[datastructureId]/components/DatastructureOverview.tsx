'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { UseMutationResult } from '@tanstack/react-query'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import {
  usePublishDatastructure,
  useUnpublishDatastructure,
  useUpdateDatastructure,
  useUpdateDatastructurePublished,
} from '@/app/services/api/datastructures/clientRequests'
import { ApiServiceResponse } from '@/app/services/api/request/apiRequest'
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
import { STATUS_TYPES, WithId } from '@/types/common'
import {
  Datastructure,
  DATASTRUCTURE_STATUS_TYPES,
  DatastructureFormAvailableSchema,
  DatastructureFormDraft,
  DatastructureFormDraftSchema,
  DatastructureStatusTypes,
  DatastructureTab,
} from '@/types/datastructures'
import { mapDatastructureVersionsApiToListData } from '@/utils/datastructures'
import { pickDirtyValues } from '@/utils/form'

import { BasicInfoTab } from './basic-info-tab/BasicInfoTab'
import { VersionsTab } from './versions-tab/VersionsTab'

const tabs: Tab<DatastructureTab>[] = [
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
  const params = useSearchParams()
  const mode = params.get('mode')
  const t = useTranslations('datastructures')
  const tCommon = useTranslations('common')

  const defaultValues: DatastructureFormDraft = {
    id: datastructure.id,
    name: datastructure.name ?? '',
    description: datastructure.description ?? '',
    dataStructureStatus: datastructure.dataStructureStatus ?? DATASTRUCTURE_STATUS_TYPES.DRAFT,
    dataStructureVersionIds: [],
    assignments: [],
  }

  const router = useRouter()

  const [selectedTab, setSelectedTab] = useState<DatastructureTab>('basicInfo')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isReadOnly, setIsReadOnly] = useState(mode !== 'edit')

  const updateDatastructure = useUpdateDatastructure()
  const updatePublishedDatastructure = useUpdateDatastructurePublished()
  const publishDatastructure = usePublishDatastructure()
  const unpublishDatastructure = useUnpublishDatastructure()
  const isLoading = updateDatastructure.isPending

  const form = useForm<DatastructureFormDraft>({
    resolver: zodResolver(DatastructureFormDraftSchema),
    mode: 'onChange',
    defaultValues,
  })

  useEffect(() => {
    form.reset(defaultValues)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [datastructure])

  const formValues = useWatch({ control: form.control })
  const statusWatch = form.watch('dataStructureStatus')
  const nameWatch = form.watch('name')
  const descriptionWatch = form.watch('description')

  const isDraftMode = statusWatch === DATASTRUCTURE_STATUS_TYPES.DRAFT
  const isInUse = datastructure.inUse

  const canSetDraft = !isInUse

  // Allow "Available" only when the form would be valid in AVAILABLE mode
  const canSetAvailable = useMemo(() => {
    return (
      DatastructureFormAvailableSchema.safeParse(formValues).success &&
      !!datastructure.dataStructureVersions.find(
        version => version.dataStructureVersionStatus === DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
      )
    )
  }, [formValues, datastructure.dataStructureVersions])

  const revalidateForm = () => {
    if (!isDraftMode) {
      void form.trigger()
    }
  }
  // Auto-revert status to draft when required fields become empty
  const revalidateDraftMode = () => {
    if (statusWatch === DATASTRUCTURE_STATUS_TYPES.AVAILABLE && !canSetAvailable) {
      form.setValue('dataStructureStatus', DATASTRUCTURE_STATUS_TYPES.DRAFT, { shouldDirty: true })
      toast.info(tCommon('info.switchMode'))
    }
  }

  useEffect(() => {
    if (isDraftMode) {
      form.clearErrors()
    } else {
      revalidateForm()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isDraftMode])

  useEffect(() => {
    revalidateDraftMode()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canSetAvailable, statusWatch])

  const completedTabs = useMemo((): DatastructureTab[] => {
    const completed: DatastructureTab[] = []
    if (nameWatch.length > 0 && descriptionWatch.length > 0) {
      completed.push('basicInfo')
    }
    return completed
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [formValues])

  const handleStatusChange = (newStatus: DatastructureStatusTypes) => {
    form.setValue('dataStructureStatus', newStatus, { shouldDirty: true })
  }

  const handleStatusUpdate = async (
    mutationFn: UseMutationResult<ApiServiceResponse<Datastructure>, unknown, WithId, unknown>,
    datastructureId: string,
  ) => {
    try {
      mutationFn.mutateAsync({ id: datastructureId })
      toast.success(tCommon('success.statusChangeSuccess'))
    } catch (error) {
      toast.error(tCommon('errors.statusChangeError'))
      throw error
    }
  }

  const handleUpdateValues = async (values: DatastructureFormDraft) => {
    try {
      if (datastructure.dataStructureStatus === STATUS_TYPES.AVAILABLE)
        await updatePublishedDatastructure.mutateAsync({ ...values, id: values.id })
      else await updateDatastructure.mutateAsync({ ...values, id: values.id })
      toast.success(t('messages.updateSuccess'))
    } catch (error) {
      toast.error(tCommon('errors.updateError', { item: tCommon('items.datastructure') }))
      throw error
    }
  }

  const handleUpdateDatastructure = async (parsedValues: DatastructureFormDraft) => {
    try {
      const dirtyFields = form.formState.dirtyFields

      const shouldPublish = !!dirtyFields.dataStructureStatus && statusWatch === DATASTRUCTURE_STATUS_TYPES.AVAILABLE
      const shouldUnpublish = !!dirtyFields.dataStructureStatus && statusWatch === DATASTRUCTURE_STATUS_TYPES.DRAFT

      const fieldsToUpdate = pickDirtyValues(parsedValues, dirtyFields)
      const shouldUpdateValues = (Object.keys(fieldsToUpdate) as (keyof DatastructureFormDraft)[]).some(
        key => key !== 'dataStructureStatus',
      )
      if (shouldUpdateValues) await handleUpdateValues(parsedValues)

      if (shouldPublish) {
        await handleStatusUpdate(publishDatastructure, parsedValues.id)
      }
      if (shouldUnpublish) {
        await handleStatusUpdate(unpublishDatastructure, parsedValues.id)
      }
      router.refresh()
    } catch (error) {
      console.error('An error occurred while submitting datastructure data.', error)
    }
    setIsExitModalOpen(false)
  }

  const submitDatastructure = async () => {
    const values = form.getValues()
    const parsed = isDraftMode
      ? DatastructureFormDraftSchema.safeParse(values)
      : DatastructureFormAvailableSchema.safeParse(values)
    if (!parsed.success) {
      console.error(parsed.error)
      toast.error(tCommon('errors.formInvalid'))
      return
    }

    await handleUpdateDatastructure(parsed.data)
  }

  const handleSave = () => {
    submitDatastructure()
  }

  const handleExit = () => {
    form.reset()
    setIsReadOnly(true)
    setIsExitModalOpen(false)
  }

  const handleExitButtonClick = () => {
    if (form.formState.isDirty) setIsExitModalOpen(true)
    else handleExit()
  }

  const isConfirmButtonDisabled = useMemo(
    () =>
      !form.formState.isDirty ||
      !!form.formState.errors.name ||
      (statusWatch !== DATASTRUCTURE_STATUS_TYPES.DRAFT && Object.keys(form.formState.errors).length > 0) ||
      isLoading,
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [isLoading, statusWatch, formValues],
  )

  const ActionButtonsAndStatusSwitch = (
    <div className="flex gap-6">
      <StatusDropdown
        statusOptions={Object.values(DATASTRUCTURE_STATUS_TYPES)}
        status={statusWatch}
        onStatusChange={handleStatusChange}
        canSetAvailable={canSetAvailable}
        canSetDraft={canSetDraft}
        statusHint={!canSetDraft ? t('messages.isInUseStatusHint') : undefined}
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
