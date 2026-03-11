'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { AxiosError } from 'axios'
import { useRouter } from 'next/navigation'
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
import { containsNonStatusField, mapDatastructureApiToFormData } from '@/utils/datastructures'
import { pickDirtyValues } from '@/utils/form'

import { tabs } from '../components/DatastructureOverview'

interface UseDatastructureProps {
  datastructure: Datastructure
}

export const useDatastructure = ({ datastructure }: UseDatastructureProps) => {
  const router = useRouter()
  const t = useTranslations('datastructures')
  const tCommon = useTranslations('common')
  const [selectedTab, setSelectedTab] = useState(tabs[0].value)

  const defaultValues = useMemo<DatastructureFormDraft>(
    () => mapDatastructureApiToFormData(datastructure),
    [datastructure],
  )

  const updateDatastructure = useUpdateDatastructure()
  const updatePublishedDatastructure = useUpdateDatastructurePublished()
  const publishDatastructure = usePublishDatastructure()
  const unpublishDatastructure = useUnpublishDatastructure()
  const isLoading =
    updateDatastructure.isPending ||
    updatePublishedDatastructure.isPending ||
    publishDatastructure.isPending ||
    unpublishDatastructure.isPending

  const form = useForm<DatastructureFormDraft>({
    resolver: zodResolver(DatastructureFormDraftSchema),
    mode: 'onChange',
    defaultValues,
  })

  const formValues = useWatch({ control: form.control })
  const statusWatch = form.watch('dataStructureStatus')
  const nameWatch = form.watch('name')
  const descriptionWatch = form.watch('description')

  const isDraftMode = statusWatch === DATASTRUCTURE_STATUS_TYPES.DRAFT
  const isInUse = !!datastructure.inUse
  const canSetDraft = !isInUse

  const hasAvailableVersion = useMemo(
    () =>
      datastructure.dataStructureVersions.some(
        version => version.dataStructureVersionStatus === DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
      ),
    [datastructure.dataStructureVersions],
  )

  const canSetAvailable = useMemo(
    () => DatastructureFormAvailableSchema.safeParse(formValues).success && hasAvailableVersion,
    [formValues, hasAvailableVersion],
  )

  useEffect(() => {
    if (isDraftMode) {
      form.clearErrors()
      return
    }
    void form.trigger()
  }, [form, isDraftMode])

  useEffect(() => {
    if (statusWatch === DATASTRUCTURE_STATUS_TYPES.AVAILABLE && !canSetAvailable) {
      form.setValue('dataStructureStatus', DATASTRUCTURE_STATUS_TYPES.DRAFT, { shouldDirty: true })
      toast.info(tCommon('info.switchMode'))
    }
  }, [canSetAvailable, form, statusWatch, tCommon])

  const completedTabs = useMemo((): DatastructureTab[] => {
    const completed: DatastructureTab[] = []
    if (nameWatch.length > 0 && descriptionWatch.length > 0) {
      completed.push('basicInfo')
    }
    if (hasAvailableVersion) {
      completed.push('versions')
    }
    return completed
  }, [descriptionWatch, hasAvailableVersion, nameWatch])

  const handleStatusChange = (newStatus: DatastructureStatusTypes) => {
    form.setValue('dataStructureStatus', newStatus, { shouldDirty: true })
  }

  const handleStatusUpdate = async (
    datastructureId: string,
    mutateAsync: (payload: WithId) => Promise<ApiServiceResponse<Datastructure>>,
  ) => {
    try {
      const response = await mutateAsync({ id: datastructureId })
      toast.success(tCommon('success.statusChangeSuccess'))
      return response.data
    } catch (error) {
      toast.error(tCommon('errors.statusChangeError'))
      throw error
    }
  }

  const handleUpdateValues = async (values: DatastructureFormDraft) => {
    try {
      let response: { data: Datastructure }
      if (datastructure.dataStructureStatus === STATUS_TYPES.AVAILABLE)
        response = await updatePublishedDatastructure.mutateAsync({
          ...values,
          createdFromDataSource: datastructure.createdFromDataSource,
        })
      else response = await updateDatastructure.mutateAsync(values)
      toast.success(t('messages.updateSuccess'))
      return response.data
    } catch (error) {
      toast.error(tCommon('errors.updateError', { item: tCommon('items.datastructure') }))
      throw error
    }
  }

  const handleUpdateDatastructure = async (parsedValues: DatastructureFormDraft) => {
    const dirtyFields = form.formState.dirtyFields

    const fieldsToUpdate = pickDirtyValues(parsedValues, dirtyFields)
    const shouldUpdateValue = containsNonStatusField(fieldsToUpdate)
    const shouldPublish = !!dirtyFields.dataStructureStatus && statusWatch === DATASTRUCTURE_STATUS_TYPES.AVAILABLE
    const shouldUnpublish = !!dirtyFields.dataStructureStatus && statusWatch === DATASTRUCTURE_STATUS_TYPES.DRAFT

    let datastructureResponse: Datastructure | null = shouldUpdateValue ? await handleUpdateValues(parsedValues) : null
    if (shouldPublish)
      datastructureResponse = await handleStatusUpdate(parsedValues.id, publishDatastructure.mutateAsync)
    if (shouldUnpublish)
      datastructureResponse = await handleStatusUpdate(parsedValues.id, unpublishDatastructure.mutateAsync)

    const updatedFormValues = datastructureResponse
      ? mapDatastructureApiToFormData(datastructureResponse)
      : parsedValues

    form.reset(updatedFormValues)
    router.refresh()
  }

  const saveDatastructure = async () => {
    const values = form.getValues()
    const parsed = isDraftMode
      ? DatastructureFormDraftSchema.safeParse(values)
      : DatastructureFormAvailableSchema.safeParse(values)
    if (!parsed.success) {
      console.error(parsed.error)
      toast.error(tCommon('errors.formInvalid'))
      return false
    }

    try {
      await handleUpdateDatastructure(parsed.data)
      return true
    } catch (error) {
      console.error('An error occurred while submitting datastructure data.', (error as AxiosError).message)
      return false
    }
  }

  const resetToInitialState = () => {
    form.reset(defaultValues)
  }

  const statusHint = !canSetDraft ? t('messages.isInUseStatusHint') : undefined

  const isConfirmButtonDisabled = useMemo(
    () =>
      !form.formState.isDirty ||
      !!form.formState.errors.name ||
      (statusWatch !== DATASTRUCTURE_STATUS_TYPES.DRAFT && Object.keys(form.formState.errors).length > 0) ||
      isLoading,
    [form.formState.errors, form.formState.isDirty, isLoading, statusWatch],
  )

  return {
    canSetAvailable,
    canSetDraft,
    completedTabs,
    form,
    isConfirmButtonDisabled,
    isLoading,
    saveDatastructure,
    statusHint,
    statusWatch,
    selectedTab,
    handleStatusChange,
    resetToInitialState,
    setSelectedTab,
  }
}
