import { zodResolver } from '@hookform/resolvers/zod'
import { AxiosError } from 'axios'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import {
  useReleaseDatasource,
  useUnreleaseDatasource,
  useUpdateDatasource,
  useUpdateDatasourceReleased,
} from '@/app/services/api/datasources/clientRequests'
import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { DataSourceDraftSchema } from '@/generated/core'
import { useError } from '@/hooks/use-error'
import { ConnectorFormToApiSchema, ConnectorStrictSchema, ConnectorType } from '@/types/connectors'
import { Datapool } from '@/types/datapools'
import {
  DATAPOOL_SCOPE_TYPES,
  DatapoolScope,
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
import { isDatastructureNotAvailableError, isResourceInUseError } from '@/utils/errors'
import { pickDirtyValues } from '@/utils/form'

export const useDatasourceForm = (
  datasource: Datasource,
  assignedGroups: GroupRoleAssignmentTable[],
  initialAssignments: GroupRoleAssignmentTable[],
  assignedDatapools: Datapool[],
  initialDatapools: Datapool[],
) => {
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')
  const { handleFormValidationError } = useError()

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
  const updateReleasedDatasource = useUpdateDatasourceReleased()
  const releaseDatasource = useReleaseDatasource()
  const unreleaseDatasource = useUnreleaseDatasource()
  const isLoading =
    updateDatasource.isPending ||
    updateReleasedDatasource.isPending ||
    releaseDatasource.isPending ||
    unreleaseDatasource.isPending

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

  // A data source a pipeline still reads from cannot be unreleased — the backend rejects
  // POST /unrelease with 409 RESOURCE_IN_USE. Gate the option here so the transition is refused
  // before the user stages an unsaveable change.
  const isInUse = !!datasource.inUse
  const canSetDraft = !isInUse

  // The connector is pinned only on the released path. The backend runs the in-use constraints
  // inside updateReleasedMeta, which a data source reaches only while it is AVAILABLE; a DRAFT one
  // still goes through the plain PATCH, and that accepts technical changes from an in-use source.
  // Gate on the persisted status — the same value handleUpdateValues routes on — so the UI is
  // neither stricter nor looser than the API.
  const isConnectorLocked = isInUse && datasource.dataSourceStatus === DATASOURCE_STATUS_TYPES.AVAILABLE

  // Zod v4 discriminatedUnion safeParse can throw on stale keys
  const canStage = useMemo(() => {
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

  // Revert to draft when required fields become empty — but never for an in-use data source
  useEffect(() => {
    if (dataSourceStatus === DATASOURCE_STATUS_TYPES.AVAILABLE && !canStage && canSetDraft) {
      form.setValue('dataSourceStatus', DATASOURCE_STATUS_TYPES.DRAFT, { shouldDirty: true })
      toast.info(tCommon('info.switchMode'))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canStage, canSetDraft, dataSourceStatus, form])

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

  const areDatapoolsDirty = useMemo(() => {
    const currentIds = new Set(assignedDatapools.map(dp => dp.id))
    const initialIds = new Set(initialDatapools.map(dp => dp.id))
    return currentIds.size !== initialIds.size || [...currentIds].some(id => !initialIds.has(id))
  }, [assignedDatapools, initialDatapools])

  const handleStatusChange = (newStatus: DatasourceStatusType) =>
    form.setValue('dataSourceStatus', newStatus, { shouldDirty: true })

  const handleStatusUpdate = async (mutateAsync: (payload: { id: string }) => Promise<{ data: Datasource }>) => {
    try {
      const response = await mutateAsync({ id: datasource.id })
      toast.success(tCommon('success.statusChangeSuccess'))
      return response.data
    } catch (error) {
      if (isResourceInUseError(error)) {
        toast.error(t('errors.inUseError'))
      } else if (isDatastructureNotAvailableError(error)) {
        toast.error(t('errors.datastructureNotAvailableError'))
      } else {
        toast.error(tCommon('errors.statusChangeError'))
      }
      throw error
    }
  }

  const handleUpdateValues = async (values: DatasourcePatchData) => {
    const hasInvalidAssignments = assignedGroups.some(group => group.assignedRoles.length === 0)

    try {
      const response =
        datasource.dataSourceStatus === DATASOURCE_STATUS_TYPES.AVAILABLE
          ? await updateReleasedDatasource.mutateAsync({ ...values, name: nameWatch })
          : await updateDatasource.mutateAsync(values)

      toast.success(tCommon('messages.updateSuccess', { item: tCommon('items.datasource') }))
      if (hasInvalidAssignments) {
        toast.error(t('errors.groupsWithoutRoles'))
      }
      return response.data
    } catch (error) {
      toast.error(t('errors.updateError'))
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

    // Validate the outbound CORE DataSource document against the generated schema before sending —
    // the frontend guarantees a schema-valid payload to the (schema-agnostic) backend, mirroring the
    // pipeline/mapping editor. Model Forge stamps $schema/id; the backend adds connectionType from the
    // connectorType, so validate that projection ({connectionType, ...connectorConfig}) here.
    if (dirtyFields.configuration && configuration && parsed.data.connectorType) {
      const coreDoc = {
        connectionType: String(parsed.data.connectorType).toLowerCase(),
        ...(configuration as Record<string, unknown>),
      }
      const coreValid = DataSourceDraftSchema.safeParse(coreDoc)
      if (!coreValid.success) {
        console.error('DataSource configuration failed CORE schema validation:', coreValid.error.issues, coreDoc)
        handleFormValidationError(coreValid.error)
        return false
      }
    }

    const assignmentsPayload = mapGroupRoleAssignmentsToApiPayload(assignedGroups)
    const areAssignmentsDirty = hasAssignmentChanges(assignedGroups, initialAssignments)

    const resolveDatapoolScope = (): DatapoolScope => {
      if (parsed.data.datapoolScope?.type === DATAPOOL_SCOPE_TYPES.ALL) return { type: DATAPOOL_SCOPE_TYPES.ALL }
      if (assignedDatapools.length > 0)
        return { type: DATAPOOL_SCOPE_TYPES.SPECIFIC, datapoolIds: assignedDatapools.map(dp => dp.id) }
      return { type: DATAPOOL_SCOPE_TYPES.NONE }
    }

    const datapoolScopePayload =
      areDatapoolsDirty || dirtyFields.datapoolScope ? { datapoolScope: resolveDatapoolScope() } : {}

    const apiPayload = {
      ...dirtyValues,
      ...(dirtyFields.configuration ? { configuration } : {}),
      ...(areAssignmentsDirty ? { assignments: assignmentsPayload } : {}),
      ...datapoolScopePayload,
      id: datasource.id,
    } as DatasourcePatchData

    const shouldUpdateValues =
      Object.keys(dirtyValues).some(key => key !== 'dataSourceStatus') || areAssignmentsDirty || areDatapoolsDirty
    const shouldRelease = hasStatusChanged && dataSourceStatus === DATASOURCE_STATUS_TYPES.AVAILABLE
    const shouldUnrelease = hasStatusChanged && dataSourceStatus === DATASOURCE_STATUS_TYPES.DRAFT

    try {
      let datasourceResponse: Datasource | null = shouldUpdateValues ? await handleUpdateValues(apiPayload) : null

      if (shouldRelease) {
        datasourceResponse = await handleStatusUpdate(releaseDatasource.mutateAsync)
      }
      if (shouldUnrelease) {
        datasourceResponse = await handleStatusUpdate(unreleaseDatasource.mutateAsync)
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

  const statusHint = !canSetDraft ? t('messages.isInUseStatusHint') : undefined

  return {
    areAssignmentsDirty,
    areDatapoolsDirty,
    form,
    dataSourceStatus,
    selectedConnectorType,
    hasStatusChanged,
    handleStatusChange,
    isDraftMode,
    canStage,
    canSetDraft,
    isConnectorLocked,
    statusHint,
    completedTabs,
    submitDatasource,
    resetToInitialState,
    isLoading,
  }
}
