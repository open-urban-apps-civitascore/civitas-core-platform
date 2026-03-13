import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import { useUpdateDatasource } from '@/app/services/api/datasources/clientRequests'
import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
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
import { hasAssignmentChanges, mapGroupRoleAssignmentsToApiPayload } from '@/utils/assignments'
import { getConnectorDefaults } from '@/utils/connectors'
import { pickDirtyValues } from '@/utils/form'

export const useDatasourceForm = (
  datasource: Datasource,
  assignedGroups: GroupRoleAssignmentTable[],
  initialAssignments: GroupRoleAssignmentTable[],
) => {
  const tCommon = useTranslations('common')
  const t = useTranslations('datasources')

  const defaultValues = useMemo(() => DatasourceApiToFormSchema.parse(datasource), [datasource])

  const [dataSourceStatus, setDataSourceStatus] = useState<DatasourceStatusType>(datasource.dataSourceStatus)

  const updateDatasource = useUpdateDatasource()
  const isLoading = updateDatasource.isPending

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
  const connectorTypeWatch = formValues.connectorType
  const nameWatch = formValues.name ?? ''

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
      setDataSourceStatus(DATASOURCE_STATUS_TYPES.DRAFT)
      toast.info(tCommon('info.switchMode'))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canSetAvailable, dataSourceStatus])

  const completedTabs = useMemo((): DatasourceTab[] => {
    const completed: DatasourceTab[] = []
    if (nameWatch.length > 0) completed.push('basicInfo')
    try {
      const { connectorType, configuration } = formValues
      if (ConnectorStrictSchema.safeParse({ connectorType, configuration }).success) completed.push('connector')
    } catch {
      // Zod v4: safeParse throws on stale keys
    }
    return completed
  }, [nameWatch, formValues])

  const handleStatusChange = (newStatus: DatasourceStatusType) => {
    setDataSourceStatus(newStatus)
  }

  const submitDatasource = (onSuccess?: () => void) => {
    const values = form.getValues()
    const parsed = isDraftMode
      ? DatasourceFormDraftSchema.safeParse(values)
      : DatasourceFormAvailableSchema.safeParse(values)
    if (!parsed.success) {
      console.error(parsed.error)
      toast.error('Form data invalid')
      return
    }

    const dirtyFields = form.formState.dirtyFields
    const dirtyValues = pickDirtyValues(parsed.data as Record<string, unknown>, dirtyFields)

    // Transform configuration to API format (needs full values for discriminated union)
    const connectorParsed = ConnectorFormToApiSchema.safeParse(parsed.data)
    const configuration = connectorParsed.success
      ? connectorParsed.data.configuration
      : (dirtyValues as Record<string, unknown>).configuration

    const areAssignmentsInvalid = assignedGroups.some(group => group.assignedRoles.length === 0)
    const assignmentsPayload = mapGroupRoleAssignmentsToApiPayload(assignedGroups)
    const areAssignmentsDirty = hasAssignmentChanges(assignedGroups, initialAssignments)

    const apiPayload = {
      ...dirtyValues,
      ...(configuration ? { configuration } : {}),
      ...(areAssignmentsDirty ? { assignments: assignmentsPayload } : {}),
      id: datasource.id,
    } as DatasourceUpdateData

    updateDatasource.mutate(apiPayload, {
      onSuccess: () => {
        if (areAssignmentsInvalid) {
          toast.error(t('errors.groupsWithoutRoles'))
        }
        onSuccess?.()
      },
    })
  }

  return {
    form,
    readyConnectorType,
    dataSourceStatus,
    handleStatusChange,
    isDraftMode,
    canSetAvailable,
    completedTabs,
    submitDatasource,
    isLoading,
  }
}
