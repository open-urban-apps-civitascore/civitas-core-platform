'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import {
  useReleaseDatastructure,
  useUnreleaseDatastructure,
  useUpdateDatastructure,
  useUpdateDatastructureReleased,
} from '@/app/services/api/datastructures/clientRequests'
import { ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { useError } from '@/hooks/use-error'
import { STATUS_TYPES, WithId } from '@/types/common'
import {
  Datastructure,
  DATASTRUCTURE_STATUS_TYPES,
  DatastructureFormAvailableSchema,
  DatastructureFormDraft,
  DatastructureFormDraftSchema,
  DatastructureStatusType,
  DatastructureTab,
} from '@/types/datastructures'
import { hasAssignmentChanges, mapGroupRoleAssignmentsToApiPayload } from '@/utils/assignments'
import { containsNonStatusField, mapDatastructureApiToFormData } from '@/utils/datastructures'
import { pickDirtyValues } from '@/utils/form'

import { tabs } from '../components/DatastructureOverview'

interface UseDatastructureProps {
  datastructure: Datastructure
  assignedGroups: GroupRoleAssignmentTable[]
  initialAssignments: GroupRoleAssignmentTable[]
}

export const useDatastructure = ({ datastructure, assignedGroups, initialAssignments }: UseDatastructureProps) => {
  const router = useRouter()
  const queryClient = useQueryClient()
  const t = useTranslations('datastructures')
  const tCommon = useTranslations('common')
  const { handleFormValidationError } = useError()
  const [selectedTab, setSelectedTab] = useState(tabs[0].value)

  const defaultValues = useMemo<DatastructureFormDraft>(
    () => mapDatastructureApiToFormData(datastructure),
    [datastructure],
  )

  const updateDatastructure = useUpdateDatastructure()
  const updateReleasedDatastructure = useUpdateDatastructureReleased()
  const releaseDatastructure = useReleaseDatastructure()
  const unreleaseDatastructure = useUnreleaseDatastructure()
  const isLoading =
    updateDatastructure.isPending ||
    updateReleasedDatastructure.isPending ||
    releaseDatastructure.isPending ||
    unreleaseDatastructure.isPending

  const form = useForm<DatastructureFormDraft>({
    resolver: zodResolver(DatastructureFormDraftSchema),
    mode: 'onChange',
    defaultValues,
  })

  const formValues = useWatch({ control: form.control })
  const datastructureStatus = form.watch('dataStructureStatus')
  const nameWatch = form.watch('name')
  const descriptionWatch = form.watch('description')

  const isDraftMode = datastructureStatus === DATASTRUCTURE_STATUS_TYPES.DRAFT
  const isInUse = !!datastructure.inUse
  const canSetDraft = !isInUse

  const hasAvailableVersion = useMemo(
    () =>
      datastructure.dataStructureVersions.some(
        version => version.dataStructureVersionStatus === DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
      ),
    [datastructure.dataStructureVersions],
  )

  const canStage = useMemo(
    () => DatastructureFormAvailableSchema.safeParse(formValues).success && hasAvailableVersion,
    [formValues, hasAvailableVersion],
  )

  const areAssignmentsDirty = useMemo(
    () => hasAssignmentChanges(assignedGroups, initialAssignments),
    [assignedGroups, initialAssignments],
  )

  useEffect(() => {
    if (isDraftMode) {
      form.clearErrors()
      return
    }
    void form.trigger()
  }, [form, isDraftMode])

  useEffect(() => {
    if (datastructureStatus === DATASTRUCTURE_STATUS_TYPES.AVAILABLE && !canStage) {
      form.setValue('dataStructureStatus', DATASTRUCTURE_STATUS_TYPES.DRAFT, { shouldDirty: true })
      toast.info(tCommon('info.switchMode'))
    }
  }, [canStage, form, datastructureStatus, tCommon])

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

  const handleStatusChange = (newStatus: DatastructureStatusType) => {
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
      const areAssignmentsInvalid = assignedGroups.some(group => group.assignedRoles.length === 0)
      const assignmentsPayload = mapGroupRoleAssignmentsToApiPayload(assignedGroups)

      const assignmentsPatch = areAssignmentsDirty ? { assignments: assignmentsPayload } : {}

      let response: { data: Datastructure }
      if (datastructure.dataStructureStatus === STATUS_TYPES.AVAILABLE)
        response = await updateReleasedDatastructure.mutateAsync({
          ...values,
          createdFromDataSource: datastructure.createdFromDataSource,
          ...assignmentsPatch,
        })
      else
        response = await updateDatastructure.mutateAsync({
          ...values,
          ...assignmentsPatch,
        })
      toast.success(t('messages.updateSuccess'))
      if (areAssignmentsInvalid) {
        toast.warning(t('errors.groupsWithoutRoles'))
      }
      return response.data
    } catch (error) {
      toast.error(tCommon('errors.updateError', { item: tCommon('items.datastructure') }))
      throw error
    }
  }

  const handleUpdateDatastructure = async (parsedValues: DatastructureFormDraft) => {
    const dirtyFields = form.formState.dirtyFields

    const fieldsToUpdate = pickDirtyValues(parsedValues, dirtyFields)
    const shouldUpdateValue = containsNonStatusField(fieldsToUpdate) || areAssignmentsDirty
    const shouldRelease =
      !!dirtyFields.dataStructureStatus && datastructureStatus === DATASTRUCTURE_STATUS_TYPES.AVAILABLE
    const shouldUnrelease =
      !!dirtyFields.dataStructureStatus && datastructureStatus === DATASTRUCTURE_STATUS_TYPES.DRAFT

    let datastructureResponse: Datastructure | null = shouldUpdateValue ? await handleUpdateValues(parsedValues) : null
    if (shouldRelease)
      datastructureResponse = await handleStatusUpdate(parsedValues.id, releaseDatastructure.mutateAsync)
    if (shouldUnrelease)
      datastructureResponse = await handleStatusUpdate(parsedValues.id, unreleaseDatastructure.mutateAsync)

    // Invalidation of datastructure version queries on datastructure name change
    // since the datastructure name is embedded in every version response
    if (datastructureResponse && datastructureResponse.name !== datastructure.name) {
      queryClient.invalidateQueries({ queryKey: [`datastructures/${datastructure.id}/versions`] })
    }

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
      handleFormValidationError(parsed.error)
      return false
    }

    try {
      await handleUpdateDatastructure(parsed.data)
      return true
    } catch (error) {
      console.error('An error occurred while submitting datastructure data.', (error as AxiosError).message)
      toast.error(tCommon('errors.updateError', { item: tCommon('items.datastructure') }))
      return false
    }
  }

  const resetToInitialState = () => {
    form.reset(defaultValues)
  }

  const statusHint = !canSetDraft ? t('messages.isInUseStatusHint') : undefined

  const isConfirmButtonDisabled = useMemo(
    () =>
      (!form.formState.isDirty && !areAssignmentsDirty) ||
      !!form.formState.errors.name ||
      (datastructureStatus !== DATASTRUCTURE_STATUS_TYPES.DRAFT && Object.keys(form.formState.errors).length > 0) ||
      isLoading,
    [areAssignmentsDirty, form.formState.errors, form.formState.isDirty, isLoading, datastructureStatus],
  )

  return {
    areAssignmentsDirty,
    canStage,
    canSetDraft,
    completedTabs,
    form,
    isConfirmButtonDisabled,
    isLoading,
    saveDatastructure,
    statusHint,
    datastructureStatus,
    selectedTab,
    handleStatusChange,
    resetToInitialState,
    setSelectedTab,
  }
}
