import { zodResolver } from '@hookform/resolvers/zod'
import { AxiosError } from 'axios'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import {
  usePublishDatasource,
  useUnpublishDatasource,
  useUpdateDatasource,
  useUpdateDatasourcePublished,
} from '@/app/services/api/datasources/clientRequests'
import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { useError } from '@/hooks/use-error'
import { ConnectorFormToApiSchema, ConnectorStrictSchema, ConnectorType } from '@/types/connectors'
import {
  Datasource,
  DATASOURCE_STATUS_TYPES,
  DatasourceApiToFormSchema,
  DatasourceFormAvailableSchema,
  DatasourceFormDraft,
  DatasourceFormDraftSchema,
  DatasourcePatchData,
  DatasourceStatusType,
  DatasourceTab,
} from '@/types/datasources'
import { hasAssignmentChanges, mapGroupRoleAssignmentsToApiPayload } from '@/utils/assignments'
import { getConnectorDefaults } from '@/utils/connectors'
import { isNameConflictError } from '@/utils/errors'
import { pickDirtyValues } from '@/utils/form'

export const useDatasourceForm = (
  datasource: Datasource,
  assignedGroups: GroupRoleAssignmentTable[],
  initialAssignments: GroupRoleAssignmentTable[],
) => {
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')
  const { handleNameError, handleFormValidationError } = useError()

  const mapDatasourceToFormValues = (source: Datasource) => {
    const parsedDatasource = DatasourceApiToFormSchema.parse(source)
    return {
      ...parsedDatasource,
      dataStructureVersionId: parsedDatasource.dataStructureVersionId ?? null,
    }
  }

  const defaultValues = useMemo(() => {
    return mapDatasourceToFormValues(datasource)
  }, [datasource])

  const [selectedConnectorType, setSelectedConnectorType] = useState<ConnectorType | undefined>(
    defaultValues.connectorType,
  )

  const updateDatasource = useUpdateDatasource()
  const updatePublishedDatasource = useUpdateDatasourcePublished()
  const publishDatasource = usePublishDatasource()
  const unpublishDatasource = useUnpublishDatasource()
  const isLoading =
    updateDatasource.isPending ||
    updatePublishedDatasource.isPending ||
    publishDatasource.isPending ||
    unpublishDatasource.isPending

  const form = useForm<DatasourceFormDraft>({
    resolver: zodResolver(DatasourceFormDraftSchema),
    mode: 'onChange',
    defaultValues,
  })

  useEffect(() => {
    form.reset(defaultValues)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [datasource])

  const formValues = useWatch({ control: form.control })
  const nameWatch = form.watch('name')
  const descriptionWatch = form.watch('description')
  const connectorTypeWatch = form.watch('connectorType')
  const dataSourceStatus = form.watch('dataSourceStatus')
  const dataStructureVersionIdWatch = form.watch('dataStructureVersionId')

  // Reset configuration on connector type change; setSelectedConnectorType gates field rendering
  // to avoid flashes while form.reset() applies the new defaults.
  useEffect(() => {
    const configDefaults = connectorTypeWatch ? getConnectorDefaults(connectorTypeWatch) : {}
    const isInitialConnectorType = defaultValues.connectorType === connectorTypeWatch
    if (isInitialConnectorType) {
      form.resetField('connectorType')
      form.resetField('configuration')
    } else {
      form.setValue('connectorType', connectorTypeWatch, {
        shouldDirty: true,
      })
      form.setValue('configuration', configDefaults, {
        shouldDirty: true,
      })
    }
    setSelectedConnectorType(connectorTypeWatch)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectorTypeWatch])

  const isDraftMode = dataSourceStatus === DATASOURCE_STATUS_TYPES.DRAFT
  const hasStatusChanged = dataSourceStatus !== datasource.dataSourceStatus

  // Zod v4 discriminatedUnion safeParse can throw on stale keys
  const canSetAvailable = useMemo(() => {
    try {
      return DatasourceFormAvailableSchema.safeParse(formValues).success
    } catch {
      return false
    }
  }, [formValues])

  // Revalidate on mode or connector type change
  // Keep name errors for showing name required error after automatic switch to draft mode when removing name
  useEffect(() => {
    if (isDraftMode) {
      const nameError = form.formState.errors.name
      form.clearErrors()
      if (nameError) {
        form.setError('name', nameError)
      }
    } else {
      void form.trigger()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectorTypeWatch, isDraftMode])

  // Revert to draft when required fields become empty
  useEffect(() => {
    if (dataSourceStatus === DATASOURCE_STATUS_TYPES.AVAILABLE && !canSetAvailable) {
      form.setValue('dataSourceStatus', DATASOURCE_STATUS_TYPES.DRAFT, { shouldDirty: true })
      toast.info(tCommon('info.switchMode'))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canSetAvailable, dataSourceStatus, form])

  const completedTabs = useMemo((): DatasourceTab[] => {
    const completed: DatasourceTab[] = []
    if (nameWatch.length > 0 && descriptionWatch.length > 0) completed.push('basicInfo')
    try {
      const { connectorType, configuration } = formValues
      if (ConnectorStrictSchema.safeParse({ connectorType, configuration }).success) completed.push('connector')
    } catch {
      // Zod v4: safeParse throws on stale keys
    }
    if (dataStructureVersionIdWatch) completed.push('dataStructure')
    return completed
  }, [nameWatch, descriptionWatch, formValues, dataStructureVersionIdWatch])

  const areAssignmentsDirty = useMemo(
    () => hasAssignmentChanges(assignedGroups, initialAssignments),
    [assignedGroups, initialAssignments],
  )

  const handleStatusChange = (newStatus: DatasourceStatusType) =>
    form.setValue('dataSourceStatus', newStatus, { shouldDirty: true })

  const handleStatusUpdate = async (mutateAsync: (payload: { id: string }) => Promise<{ data: Datasource }>) => {
    try {
      const response = await mutateAsync({ id: datasource.id })
      toast.success(tCommon('success.statusChangeSuccess'))
      return response.data
    } catch (error) {
      toast.error(tCommon('errors.statusChangeError'))
      throw error
    }
  }

  const handleUpdateValues = async (values: DatasourcePatchData) => {
    const hasInvalidAssignments = assignedGroups.some(group => group.assignedRoles.length === 0)

    try {
      const response =
        datasource.dataSourceStatus === DATASOURCE_STATUS_TYPES.AVAILABLE
          ? await updatePublishedDatasource.mutateAsync({ ...values, name: nameWatch })
          : await updateDatasource.mutateAsync(values)

      toast.success(tCommon('messages.updateSuccess', { item: tCommon('items.datasource') }))
      if (hasInvalidAssignments) {
        toast.error(t('errors.groupsWithoutRoles'))
      }
      return response.data
    } catch (error) {
      if (isNameConflictError(error as AxiosError)) {
        handleNameError(form, values.name)
      } else toast.error(t('errors.updateError'))
      throw error
    }
  }

  const submitDatasource = async (onSuccess?: () => void): Promise<boolean> => {
    const values = form.getValues()
    const parsed = isDraftMode
      ? DatasourceFormDraftSchema.safeParse(values)
      : DatasourceFormAvailableSchema.safeParse(values)
    if (!parsed.success) {
      handleFormValidationError(parsed.error)
      return false
    }

    const dirtyFields = form.formState.dirtyFields
    const dirtyValues = pickDirtyValues(parsed.data as Record<string, unknown>, dirtyFields)

    // Transform configuration to API format (needs full values for discriminated union)
    const connectorParsed = ConnectorFormToApiSchema.safeParse(parsed.data)
    const configuration = connectorParsed.success
      ? connectorParsed.data.configuration
      : (dirtyValues as Record<string, unknown>).configuration

    const assignmentsPayload = mapGroupRoleAssignmentsToApiPayload(assignedGroups)
    const areAssignmentsDirty = hasAssignmentChanges(assignedGroups, initialAssignments)

    const apiPayload = {
      ...dirtyValues,
      ...(dirtyFields.configuration ? { configuration } : {}),
      ...(areAssignmentsDirty ? { assignments: assignmentsPayload } : {}),
      id: datasource.id,
    } as DatasourcePatchData

    const shouldUpdateValues = Object.keys(dirtyValues).some(key => key !== 'dataSourceStatus') || areAssignmentsDirty
    const shouldPublish = hasStatusChanged && dataSourceStatus === DATASOURCE_STATUS_TYPES.AVAILABLE
    const shouldUnpublish = hasStatusChanged && dataSourceStatus === DATASOURCE_STATUS_TYPES.DRAFT

    try {
      let datasourceResponse: Datasource | null = shouldUpdateValues ? await handleUpdateValues(apiPayload) : null

      if (shouldPublish) {
        datasourceResponse = await handleStatusUpdate(publishDatasource.mutateAsync)
      }
      if (shouldUnpublish) {
        datasourceResponse = await handleStatusUpdate(unpublishDatasource.mutateAsync)
      }

      if (datasourceResponse) {
        form.reset(mapDatasourceToFormValues(datasourceResponse))
      }

      onSuccess?.()
      return true
    } catch (error) {
      console.error('An error occurred while submitting datasource data.', (error as AxiosError).message)
      return false
    }
  }

  const resetToInitialState = () => {
    form.reset(defaultValues)
  }

  return {
    areAssignmentsDirty,
    form,
    dataSourceStatus,
    selectedConnectorType,
    hasStatusChanged,
    handleStatusChange,
    isDraftMode,
    canSetAvailable,
    completedTabs,
    submitDatasource,
    resetToInitialState,
    isLoading,
  }
}
