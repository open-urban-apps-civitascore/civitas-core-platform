import { zodResolver } from '@hookform/resolvers/zod'
import { AxiosError } from 'axios'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import {
  usePublishDatasource,
  useUnpublishDatasource,
  useUpdateDatasource,
  useUpdateDatasourcePublished,
} from '@/app/services/api/datasources/clientRequests'
import { ConnectorFormToApiSchema, ConnectorStrictSchema, ConnectorType } from '@/types/connectors'
import {
  Datasource,
  DATASOURCE_STATUS_TYPES,
  DatasourceApiToFormSchema,
  DatasourceFormAvailableSchema,
  DatasourceFormDraft,
  DatasourceFormDraftSchema,
  DatasourceStatusType,
  DatasourceTab,
  DatasourceUpdateData,
} from '@/types/datasources'
import { getConnectorDefaults } from '@/utils/connectors'
import { pickDirtyValues } from '@/utils/form'

export const useDatasourceForm = (datasource: Datasource) => {
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')

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

  const updateDatasource = useUpdateDatasource()
  const updatePublishedDatasource = useUpdateDatasourcePublished()
  const publishDatasource = usePublishDatasource()
  const unpublishDatasource = useUnpublishDatasource()
  const isLoading =
    updateDatasource.isPending ||
    updatePublishedDatasource.isPending ||
    publishDatasource.isPending ||
    unpublishDatasource.isPending

  const handleRequestError = (error: unknown) => {
    if ((error as AxiosError).response?.status === 409) {
      form.setError('name', { type: 'manual', message: 'common.errors.nameExists' })
      toast.error(tCommon('errors.nameExists'))
    } else toast.error(t('errors.updateError'))
  }

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
  const dataSourceStatus = form.watch('dataSourceStatus')
  const connectorTypeWatch = formValues.connectorType
  const nameWatch = formValues.name ?? ''
  const dataStructureVersionIdWatch = form.watch('dataStructureVersionId')

  // Reset configuration on connector type change; readyConnectorType gates field rendering
  // to avoid flashes while form.reset() applies the new defaults.
  const prevConnectorType = useRef<ConnectorType | undefined>(connectorTypeWatch)
  const [readyConnectorType, setReadyConnectorType] = useState<ConnectorType | undefined>(connectorTypeWatch)
  useEffect(() => {
    if (connectorTypeWatch && prevConnectorType.current !== connectorTypeWatch) {
      prevConnectorType.current = connectorTypeWatch
      const defaults = getConnectorDefaults(connectorTypeWatch)
      form.reset(
        { ...form.getValues(), connectorType: connectorTypeWatch, configuration: defaults },
        { keepDirty: true, keepTouched: true },
      )
      setReadyConnectorType(connectorTypeWatch)
    }
  }, [connectorTypeWatch, form])

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
  useEffect(() => {
    if (isDraftMode) {
      form.clearErrors()
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
    if (nameWatch.length > 0) completed.push('basicInfo')
    try {
      const { connectorType, configuration } = formValues
      if (ConnectorStrictSchema.safeParse({ connectorType, configuration }).success) completed.push('connector')
    } catch {
      // Zod v4: safeParse throws on stale keys
    }
    if (dataStructureVersionIdWatch) completed.push('dataStructure')
    return completed
  }, [nameWatch, formValues, dataStructureVersionIdWatch])

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

  const handleUpdateValues = async (values: DatasourceUpdateData) => {
    try {
      const response =
        datasource.dataSourceStatus === DATASOURCE_STATUS_TYPES.AVAILABLE
          ? await updatePublishedDatasource.mutateAsync({ ...values, name: nameWatch })
          : await updateDatasource.mutateAsync(values)

      toast.success(tCommon('messages.updateSuccess', { item: tCommon('items.datasource') }))
      return response.data
    } catch (error) {
      handleRequestError(error)
      throw error
    }
  }

  const submitDatasource = (onSuccess?: () => void) => {
    void (async () => {
      const values = form.getValues()
      const parsed = isDraftMode
        ? DatasourceFormDraftSchema.safeParse(values)
        : DatasourceFormAvailableSchema.safeParse(values)
      if (!parsed.success) {
        console.error(parsed.error)
        toast.error(tCommon('errors.formInvalid'))
        return
      }

      const dirtyFields = form.formState.dirtyFields
      const dirtyValues = pickDirtyValues(parsed.data as Record<string, unknown>, dirtyFields)

      // Transform configuration to API format (needs full values for discriminated union)
      const connectorParsed = ConnectorFormToApiSchema.safeParse(parsed.data)
      const configuration = connectorParsed.success
        ? connectorParsed.data.configuration
        : (dirtyValues as Record<string, unknown>).configuration

      const apiPayload = {
        ...dirtyValues,
        ...(dirtyFields.configuration ? { configuration } : {}),
        id: datasource.id,
      } as DatasourceUpdateData

      const shouldUpdateValues = Object.keys(dirtyValues).some(key => key !== 'dataSourceStatus')
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
      } catch (error) {
        console.error('An error occurred while submitting datasource data.', (error as AxiosError).message)
      }
    })()
  }

  const resetToInitialState = () => {
    form.reset(defaultValues)
  }

  return {
    form,
    readyConnectorType,
    dataSourceStatus,
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
