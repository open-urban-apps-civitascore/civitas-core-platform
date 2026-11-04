import { zodResolver } from '@hookform/resolvers/zod'
import { AxiosError } from 'axios'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useRef, useState } from 'react'
import { get, useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import {
  useReleaseDatasource,
  useUnreleaseDatasource,
  useUpdateDatasource,
  useUpdateDatasourceReleased,
} from '@/app/services/api/datasources/clientRequests'
import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import { CONNECTOR_TYPES } from '@/const/connectors'
import { DataSourceDraftSchema } from '@/generated/core'
import { useError } from '@/hooks/use-error'
import {
  ConnectorFormToApiSchema,
  ConnectorTabCompleteSchema,
  ConnectorType,
  emptyHttpAuthentication,
  HttpApiCallsStrictSchema,
  HttpAuthenticationType,
  HttpConnectionForm,
  SECRET_PLACEHOLDER,
} from '@/types/connectors'
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
  DatasourceMetaPatchData,
  DatasourcePatchData,
  DatasourceStatusType,
  DatasourceTab,
} from '@/types/datasources'
import { hasAssignmentChanges, mapGroupRoleAssignmentsToApiPayload } from '@/utils/assignments'
import { findSecretsToReenter, getConnectorDefaults, HTTP_SECRET_PATHS, toFormConnectorPath } from '@/utils/connectors'
import {
  getConnectorFieldErrorPaths,
  isDatastructureNotAvailableError,
  isResourceInUseError,
  isStoredSecretRefusedError,
} from '@/utils/errors'
import { hasDirtyField, pickDirtyValues } from '@/utils/form'

const REENTER_SECRET_MESSAGE = 'datasources.errors.reenterSecret'

const REJECTED_BY_SERVER_MESSAGE = 'datasources.errors.rejectedByServer'

const AUTHENTICATION_TYPE_PATH = 'configuration.authentication.type'

type ConnectorValues = { connectorType?: ConnectorType; configuration?: Record<string, unknown> }

const getSecretsToReenter = (values: ConnectorValues, loadedValues: ConnectorValues | undefined) => {
  if (values.connectorType !== CONNECTOR_TYPES.HTTP || !values.configuration) return []
  const isLoadedHttp = loadedValues?.connectorType === CONNECTOR_TYPES.HTTP
  const loaded = isLoadedHttp ? (loadedValues.configuration as HttpConnectionForm | undefined) : undefined
  return findSecretsToReenter(values.configuration as HttpConnectionForm, loaded)
}

const toConnectorPayloadKey = (values: unknown) => {
  const parsed = ConnectorFormToApiSchema.safeParse(values)
  return parsed.success ? JSON.stringify(parsed.data) : undefined
}

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
  const { isDirty, dirtyFields, defaultValues: initialFormValues } = form.formState
  const hasDirtyFieldOutsideConfiguration = Object.entries(dirtyFields).some(
    ([key, value]) => key !== 'configuration' && hasDirtyField(value),
  )
  const connectorPayloadKey = useMemo(() => toConnectorPayloadKey(formValues), [formValues])
  const initialConnectorPayloadKey = useMemo(() => toConnectorPayloadKey(initialFormValues), [initialFormValues])
  const isConnectorChanged =
    !connectorPayloadKey || !initialConnectorPayloadKey || connectorPayloadKey !== initialConnectorPayloadKey
  const isFormDirty = isDirty && (hasDirtyFieldOutsideConfiguration || isConnectorChanged)
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

  const setReenterSecretErrors = () => {
    for (const path of getSecretsToReenter(form.getValues(), form.formState.defaultValues)) {
      form.setError(`configuration.${path}`, { type: 'custom', message: REENTER_SECRET_MESSAGE })
    }
  }

  const authenticationTypeWatch = useWatch({
    control: form.control,
    name: AUTHENTICATION_TYPE_PATH,
  }) as HttpAuthenticationType | undefined
  const isFirstAuthenticationTypeRender = useRef(true)

  useEffect(() => {
    if (connectorTypeWatch !== CONNECTOR_TYPES.HTTP || !authenticationTypeWatch) return
    const isLoadedHttpConnector = initialFormValues?.connectorType === CONNECTOR_TYPES.HTTP
    if (isLoadedHttpConnector && get(initialFormValues, AUTHENTICATION_TYPE_PATH) === authenticationTypeWatch) {
      form.resetField('configuration.authentication')
    } else {
      form.setValue('configuration.authentication', emptyHttpAuthentication(authenticationTypeWatch), {
        shouldDirty: true,
      })
    }
    if (isFirstAuthenticationTypeRender.current) {
      isFirstAuthenticationTypeRender.current = false
      return
    }
    const revalidate = async () => {
      await form.trigger(['configuration.authentication', 'configuration.baseUrl'])
      setReenterSecretErrors()
    }
    void revalidate()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authenticationTypeWatch])

  const secretsToReenter = useMemo(
    () => getSecretsToReenter(formValues, initialFormValues),
    [formValues, initialFormValues],
  )

  const secretsToReenterKey = secretsToReenter.join(',')

  useEffect(() => {
    setReenterSecretErrors()
    const resolvedPaths = HTTP_SECRET_PATHS.filter(path => !secretsToReenter.includes(path))
    for (const path of resolvedPaths) {
      const fieldPath = `configuration.${path}` as const
      if (form.getFieldState(fieldPath).error?.message === REENTER_SECRET_MESSAGE) {
        form.clearErrors(fieldPath)
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [secretsToReenterKey])

  const isDraftMode = dataSourceStatus === DATASOURCE_STATUS_TYPES.DRAFT
  const hasStatusChanged = dataSourceStatus !== datasource.dataSourceStatus

  // A data source a released dataset's pipeline still reads from cannot be unreleased — the backend
  // rejects POST /unrelease with 409 RESOURCE_IN_USE (requireNoReleasedReferrer). Draft referrers do
  // not block it. Gate the option here so the transition is refused before the user stages an
  // unsaveable change.
  const isInUseByReleased = datasource.inUseByReleased
  const canSetDraft = !isInUseByReleased

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
      setReenterSecretErrors()
    } else {
      const revalidate = async () => {
        await form.trigger()
        setReenterSecretErrors()
      }
      void revalidate()
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
      if (ConnectorTabCompleteSchema.safeParse({ connectorType, configuration }).success) completed.push('connector')
      const isHttpConnector = connectorType === CONNECTOR_TYPES.HTTP
      if (isHttpConnector && HttpApiCallsStrictSchema.safeParse(configuration?.apiCalls).success) {
        completed.push('apiCalls')
      }
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

  const toReleasedPayload = (values: DatasourcePatchData): DatasourceMetaPatchData => {
    const { id, description, datapoolScope, assignments } = values
    return { id, name: nameWatch, description, datapoolScope, assignments }
  }

  const serverErrorPaths = useRef<string[]>([])

  const clearServerErrors = () => {
    for (const path of serverErrorPaths.current) {
      const fieldPath = `configuration.${path}` as const
      if (form.getFieldState(fieldPath).error?.message === REJECTED_BY_SERVER_MESSAGE) {
        form.clearErrors(fieldPath)
      }
    }
    serverErrorPaths.current = []
  }

  const setMaskedSecretErrors = () => {
    const configuration = form.getValues('configuration')
    const maskedSecretPaths = HTTP_SECRET_PATHS.filter(path => get(configuration, path) === SECRET_PLACEHOLDER)
    for (const path of maskedSecretPaths) {
      form.setError(`configuration.${path}`, { type: 'custom', message: REENTER_SECRET_MESSAGE })
    }
  }

  const handleUpdateValues = async (values: DatasourcePatchData) => {
    const hasInvalidAssignments = assignedGroups.some(group => group.assignedRoles.length === 0)

    try {
      const response =
        datasource.dataSourceStatus === DATASOURCE_STATUS_TYPES.AVAILABLE
          ? await updateReleasedDatasource.mutateAsync(toReleasedPayload(values))
          : await updateDatasource.mutateAsync(values)

      toast.success(tCommon('messages.updateSuccess', { item: tCommon('items.datasource') }))
      if (hasInvalidAssignments) {
        toast.error(t('errors.groupsWithoutRoles'))
      }
      return response.data
    } catch (error) {
      const isStoredSecretRefused = isStoredSecretRefusedError(error)
      if (isStoredSecretRefused) {
        setMaskedSecretErrors()
      }
      const configuration = form.getValues('configuration')
      const formPaths = (getConnectorFieldErrorPaths(error) ?? []).map(path => toFormConnectorPath(path, configuration))
      serverErrorPaths.current = formPaths.filter((path): path is string => !!path)
      for (const path of serverErrorPaths.current) {
        form.setError(`configuration.${path}`, { type: 'server', message: REJECTED_BY_SERVER_MESSAGE })
      }
      toast.error(t(isStoredSecretRefused ? 'errors.storedSecretRefused' : 'errors.updateError'))
      throw error
    }
  }

  const submitDatasource = async (onSuccess?: () => void): Promise<boolean> => {
    clearServerErrors()
    const values = form.getValues()
    const parsed = isDraftMode
      ? DatasourceFormDraftSchema.safeParse(values)
      : DatasourceFormAvailableSchema.safeParse(values)
    if (!parsed.success) {
      handleFormValidationError(parsed.error)
      return false
    }
    if (secretsToReenter.length) {
      setReenterSecretErrors()
      handleFormValidationError()
      return false
    }

    const dirtyFields = form.formState.dirtyFields
    const {
      connectorType: _connectorType,
      configuration: _configuration,
      dataSourceStatus: _dataSourceStatus,
      ...dirtyValues
    } = pickDirtyValues(parsed.data as Record<string, unknown>, dirtyFields)

    // The backend replaces the stored connector when the type differs and merges it otherwise, so
    // sending every field of the selected type is correct in both cases.
    const isConnectorDirty = hasDirtyField(dirtyFields.connectorType) || hasDirtyField(dirtyFields.configuration)
    const connectorParsed = isConnectorDirty ? ConnectorFormToApiSchema.safeParse(parsed.data) : null
    const connector = connectorParsed?.success ? connectorParsed.data : undefined

    // Validate the outbound CORE DataSource document against the generated schema before sending —
    // the frontend guarantees a schema-valid payload to the (schema-agnostic) backend, mirroring the
    // pipeline/mapping editor. Model Forge stamps $schema/id; the backend adds connectionType from the
    // connectorType, so validate that projection ({connectionType, ...connectorConfig}) here.
    if (connector) {
      const { connectorType, ...connectorFields } = connector
      const coreDoc = { connectionType: connectorType.toLowerCase(), ...connectorFields }
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
      ...(connector ? { connector } : {}),
      ...(areAssignmentsDirty ? { assignments: assignmentsPayload } : {}),
      ...datapoolScopePayload,
      id: datasource.id,
    } as DatasourcePatchData

    const shouldUpdateValues =
      Object.keys(dirtyValues).length > 0 || !!connector || areAssignmentsDirty || areDatapoolsDirty
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

  return {
    areAssignmentsDirty,
    areDatapoolsDirty,
    isFormDirty,
    isConnectorChanged,
    form,
    dataSourceStatus,
    selectedConnectorType,
    hasStatusChanged,
    handleStatusChange,
    isDraftMode,
    canStage,
    canSetDraft,
    completedTabs,
    submitDatasource,
    resetToInitialState,
    isLoading,
  }
}
