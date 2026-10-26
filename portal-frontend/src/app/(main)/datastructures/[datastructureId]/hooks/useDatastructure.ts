'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { AxiosError } from 'axios'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { useUpdateDatastructure } from '@/app/services/api/datastructures/clientRequests'
import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { useError } from '@/hooks/use-error'
import {
  Datastructure,
  DATASTRUCTURE_STATUS_TYPES,
  DatastructureFormData,
  DatastructureFormSchema,
  DatastructureTab,
} from '@/types/datastructures'
import { hasAssignmentChanges, mapGroupRoleAssignmentsToApiPayload } from '@/utils/assignments'
import { mapDatastructureApiToFormData } from '@/utils/datastructures'
import { isResourceInUseError } from '@/utils/errors'

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

  const defaultValues = useMemo<DatastructureFormData>(
    () => mapDatastructureApiToFormData(datastructure),
    [datastructure],
  )

  const updateDatastructure = useUpdateDatastructure()
  const isLoading = updateDatastructure.isPending

  const form = useForm<DatastructureFormData>({
    resolver: zodResolver(DatastructureFormSchema),
    mode: 'onChange',
    defaultValues,
  })

  const nameWatch = form.watch('name')
  const descriptionWatch = form.watch('description')

  const hasAvailableVersion = useMemo(
    () =>
      datastructure.dataStructureVersions.some(
        version => version.dataStructureVersionStatus === DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
      ),
    [datastructure.dataStructureVersions],
  )

  const areAssignmentsDirty = useMemo(
    () => hasAssignmentChanges(assignedGroups, initialAssignments),
    [assignedGroups, initialAssignments],
  )

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

  const handleUpdateValues = async (values: DatastructureFormData) => {
    try {
      const areAssignmentsInvalid = assignedGroups.some(group => group.assignedRoles.length === 0)
      const assignmentsPayload = mapGroupRoleAssignmentsToApiPayload(assignedGroups)

      const assignmentsPatch = areAssignmentsDirty ? { assignments: assignmentsPayload } : {}

      const response = await updateDatastructure.mutateAsync({
        ...values,
        ...assignmentsPatch,
      })
      toast.success(t('messages.updateSuccess'))
      if (areAssignmentsInvalid) {
        toast.warning(t('errors.groupsWithoutRoles'))
      }
      return response.data
    } catch (error) {
      toast.error(
        isResourceInUseError(error)
          ? t('errors.inUseError')
          : tCommon('errors.updateError', { item: tCommon('items.datastructure') }),
      )
      throw error
    }
  }

  const handleUpdateDatastructure = async (parsedValues: DatastructureFormData, shouldRefresh = true) => {
    const datastructureResponse = await handleUpdateValues(parsedValues)

    // Invalidation of datastructure version queries on datastructure name change
    // since the datastructure name is embedded in every version response
    if (datastructureResponse.name !== datastructure.name) {
      queryClient.invalidateQueries({ queryKey: [`datastructures/${datastructure.id}/versions`] })
    }

    form.reset(mapDatastructureApiToFormData(datastructureResponse))
    if (shouldRefresh) router.refresh()
  }

  const saveDatastructure = async (shouldRefresh = true) => {
    const values = form.getValues()
    const parsed = DatastructureFormSchema.safeParse(values)
    if (!parsed.success) {
      handleFormValidationError(parsed.error)
      return false
    }

    try {
      await handleUpdateDatastructure(parsed.data, shouldRefresh)
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

  const isConfirmButtonDisabled = useMemo(
    () =>
      (!form.formState.isDirty && !areAssignmentsDirty) || Object.keys(form.formState.errors).length > 0 || isLoading,
    [areAssignmentsDirty, form.formState.errors, form.formState.isDirty, isLoading],
  )

  return {
    areAssignmentsDirty,
    completedTabs,
    form,
    isConfirmButtonDisabled,
    isLoading,
    saveDatastructure,
    selectedTab,
    resetToInitialState,
    setSelectedTab,
  }
}
