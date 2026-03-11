'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { AxiosError } from 'axios'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { FieldErrors, useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import {
  useCreateDatastructureVersion,
  usePublishDatastructureVersion,
  useUnpublishDatastructureVersion,
  useUpdateDatastructureVersion,
  useUpdateDatastructureVersionPublished,
} from '@/app/services/api/datastructures/versions/clientRequests'
import { ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { useMultiSessionManager } from '@/components/uml-modeler/hooks/use-multi-session-manager'
import { buildUMLModelPayload } from '@/components/uml-modeler/services/modelUploadService'
import { QUERY_PARAMS } from '@/const/searchParams'
import { STATUS_TYPES } from '@/types/common'
import {
  DATASTRUCTURE_STATUS_TYPES,
  DATASTRUCTURE_VERSION_SOURCE,
  DatastructureStatusTypes,
  DatastructureVersion,
  DatastructureVersionCreateData,
  DatastructureVersionFormAvailableSchema,
  DatastructureVersionFormData,
  DatastructureVersionFormDraftSchema,
  DatastructureVersionPutData,
  DatastructureVersionTab,
} from '@/types/datastructures'
import {
  buildSessionFromVersion,
  containsNonStatusField,
  mapDatastructureVersionApiToFormData,
  mapDatastructureVersionFormToApiData,
  parseDatastructureVersionFormData,
} from '@/utils/datastructures'
import { pickDirtyValues } from '@/utils/form'

export const defaultDatastructureVersionFormData: DatastructureVersionFormData = {
  id: '',
  version: '',
  description: '',
  dataStructureVersionSource: DATASTRUCTURE_VERSION_SOURCE.OWN,
  dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.DRAFT,
  modelAtlasUri: null,
  modelName: null,
  nodes: [],
  edges: [],
}

type OtherVersion = { id: string; version: string; dataStructureVersionStatus: DatastructureStatusTypes }

interface UseDatastructureVersionProps {
  datastructureId: string
  version: DatastructureVersion | null
  isCreateMode: boolean
  otherVersions: OtherVersion[]
  isDatastructureAvailable: boolean
  subTabValue: string
}

export const useDatastructureVersion = ({
  datastructureId,
  version,
  isCreateMode,
  otherVersions,
  isDatastructureAvailable,
  subTabValue,
}: UseDatastructureVersionProps) => {
  const router = useRouter()
  const t = useTranslations('datastructureVersions')
  const tCommon = useTranslations('common')
  const [initialSession, setInitialSession] = useState(() => buildSessionFromVersion(version))

  const updateVersion = useUpdateDatastructureVersion(datastructureId)
  const updatePublishedVersion = useUpdateDatastructureVersionPublished(datastructureId)
  const createVersion = useCreateDatastructureVersion(datastructureId)
  const publishVersion = usePublishDatastructureVersion(datastructureId)
  const unpublishVersion = useUnpublishDatastructureVersion(datastructureId)

  const isLoading =
    updateVersion.isPending ||
    createVersion.isPending ||
    publishVersion.isPending ||
    unpublishVersion.isPending ||
    updatePublishedVersion.isPending

  const modelSessionManager = useMultiSessionManager({ initialSession })
  const nodes = modelSessionManager.activeSession?.diagram.nodes
  const edges = modelSessionManager.activeSession?.diagram.edges
  const modelName = modelSessionManager.activeSession?.diagram.name
  const activeSession = modelSessionManager.activeSession
  const activeSessionId = modelSessionManager.activeSessionId
  const shouldMarkModelFieldsDirty = !!activeSession?.isDirty

  const initialFormValues = useMemo(
    () => (version ? mapDatastructureVersionApiToFormData(version) : defaultDatastructureVersionFormData),
    [version],
  )

  const form = useForm<DatastructureVersionFormData>({
    resolver: zodResolver(DatastructureVersionFormDraftSchema),
    mode: 'onChange',
    defaultValues: initialFormValues,
  })

  const formValues = useWatch({ control: form.control })
  const descriptionWatch = form.watch('description')
  const statusWatch = form.watch('dataStructureVersionStatus')
  const versionWatch = form.watch('version')
  const nodesWatch = form.watch('nodes')
  const modelUriWatch = form.watch('modelAtlasUri')
  const modelNameWatch = form.watch('modelName')
  const sourceWatch = form.watch('dataStructureVersionSource')

  const isDraftMode = statusWatch === DATASTRUCTURE_STATUS_TYPES.DRAFT
  const modelUri = `http://civitas.org/model/${datastructureId}/${versionWatch}`

  useEffect(() => {
    form.setValue('modelName', modelName || null, { shouldDirty: shouldMarkModelFieldsDirty })
  }, [modelName, form, shouldMarkModelFieldsDirty])

  useEffect(() => {
    form.setValue('edges', edges || [], { shouldDirty: shouldMarkModelFieldsDirty })
  }, [edges, form, shouldMarkModelFieldsDirty])

  /**
   * Set form values nodes and modelAtlasUri depending on the nodes amount in the diagram
   * If there are no nodes, the diagram is considered not existing, and modelAtlasUri gets set to null
   */
  useEffect(() => {
    form.setValue('nodes', nodes || [], { shouldDirty: shouldMarkModelFieldsDirty })
    const hasDiagram = nodes && nodes.length > 0
    const modelAtlasUriValue = hasDiagram ? modelUri : null
    form.setValue('modelAtlasUri', modelAtlasUriValue, {
      shouldDirty: shouldMarkModelFieldsDirty,
    })
  }, [nodes, form, modelUri, shouldMarkModelFieldsDirty])

  /**
   * after closing the diagram, a new session gets created. This session is clean.
   * Therefore, it has to be checked if it is still the same session, based on the session id.
   * Also, set all model form values to null after closing the session.
   */
  useEffect(() => {
    if (!activeSession?.isDirty && activeSessionId !== initialSession?.id) {
      form.setValue('modelAtlasUri', null, { shouldDirty: true })
      form.setValue('modelName', null, { shouldDirty: true })
      form.setValue('nodes', [], { shouldDirty: true })
      form.setValue('edges', [], { shouldDirty: true })
    }
  }, [activeSession, initialSession, form, activeSessionId])

  const versionAlreadyExistsError = useMemo(() => {
    const versionExists = otherVersions.find(otherVersion => otherVersion.version === versionWatch.trim())
    if (versionExists) {
      return t('errors.versionAlreadyExists')
    }
    return undefined
  }, [otherVersions, versionWatch, t])

  const isInUse = version?.inUse || false

  const isLastAvailableVersionInAvailableDatastructure =
    isDatastructureAvailable &&
    !otherVersions.some(
      otherVersion => otherVersion.dataStructureVersionStatus === DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
    )

  const canSetDraft = !isInUse && !isLastAvailableVersionInAvailableDatastructure

  const canSetAvailable = useMemo(() => {
    return DatastructureVersionFormAvailableSchema.safeParse(formValues).success
  }, [formValues])

  const revalidateForm = () => {
    if (!isDraftMode) {
      void form.trigger()
    }
  }

  const revalidateDraftMode = () => {
    if (statusWatch === DATASTRUCTURE_STATUS_TYPES.AVAILABLE && !canSetAvailable) {
      form.setValue('dataStructureVersionStatus', DATASTRUCTURE_STATUS_TYPES.DRAFT, { shouldDirty: true })
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

  const completedTabs = useMemo((): DatastructureVersionTab[] => {
    const completed: DatastructureVersionTab[] = []
    if (versionWatch.length > 0 && descriptionWatch.length > 0 && sourceWatch) completed.push('versionInfo')
    if (nodesWatch.length > 0 && modelUriWatch && modelNameWatch) completed.push('structure')
    return completed
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [formValues])

  const handleStatusChange = (newStatus: DatastructureStatusTypes) => {
    form.setValue('dataStructureVersionStatus', newStatus, { shouldDirty: true })
  }

  const handleStatusUpdate = async (
    versionId: string,
    mutateAsync: (payload: { id: string }) => Promise<ApiServiceResponse<DatastructureVersion>>,
  ): Promise<DatastructureVersion> => {
    try {
      const response = await mutateAsync({ id: versionId })
      toast.success(tCommon('success.statusChangeSuccess'))
      return response.data
    } catch (error) {
      toast.error(tCommon('errors.statusChangeError'))
      throw error
    }
  }

  const handleCreateVersion = async (createData: DatastructureVersionCreateData) => {
    const isStatusFieldDirty = form.formState.dirtyFields.dataStructureVersionStatus
    const shouldPublish = !!isStatusFieldDirty && statusWatch === DATASTRUCTURE_STATUS_TYPES.AVAILABLE

    let data: DatastructureVersion
    try {
      const response = await createVersion.mutateAsync(createData)
      data = response.data
    } catch (error) {
      toast.error(t('errors.creationError'))
      throw error
    }

    toast.success(t('messages.createSuccess'))

    if (shouldPublish) await handleStatusUpdate(data.id, publishVersion.mutateAsync)

    router.push(`/datastructures/${datastructureId}/${data.id}?mode=edit&${QUERY_PARAMS.subTabValue}=${subTabValue}`)
  }

  const handleUpdateValues = async (values: DatastructureVersionPutData): Promise<DatastructureVersion> => {
    try {
      let response: { data: DatastructureVersion }
      if (initialFormValues.dataStructureVersionStatus === STATUS_TYPES.AVAILABLE)
        response = await updatePublishedVersion.mutateAsync({ ...values, id: values.id })
      else response = await updateVersion.mutateAsync({ ...values, id: values.id })
      toast.success(t('messages.updateSuccess'))
      return response.data
    } catch (error) {
      toast.error(tCommon('errors.updateError', { item: tCommon('items.datastructureVersion') }))
      throw error
    }
  }

  const handleUpdateVersion = async (
    parsedValues: DatastructureVersionPutData,
    parsedFormValues: DatastructureVersionFormData,
  ) => {
    const dirtyFields = form.formState.dirtyFields
    const fieldsToUpdate = pickDirtyValues(formValues, dirtyFields)

    const shouldUpdateValues = containsNonStatusField(fieldsToUpdate)
    const shouldPublish =
      !!dirtyFields.dataStructureVersionStatus && statusWatch === DATASTRUCTURE_STATUS_TYPES.AVAILABLE
    const shouldUnpublish = !!dirtyFields.dataStructureVersionStatus && statusWatch === DATASTRUCTURE_STATUS_TYPES.DRAFT

    let versionResponse: DatastructureVersion | null = shouldUpdateValues
      ? await handleUpdateValues(parsedValues)
      : null
    if (shouldPublish) versionResponse = await handleStatusUpdate(parsedValues.id, publishVersion.mutateAsync)
    if (shouldUnpublish) versionResponse = await handleStatusUpdate(parsedValues.id, unpublishVersion.mutateAsync)

    const updatedFormValues = versionResponse ? mapDatastructureVersionApiToFormData(versionResponse) : parsedFormValues
    resetFormAndSession(updatedFormValues, versionResponse || version)

    router.refresh()
  }

  const resetFormAndSession = (
    formValuesToReset: DatastructureVersionFormData,
    versionDataForSession: DatastructureVersion | null,
  ) => {
    form.reset(formValuesToReset)
    if (!activeSessionId) return
    const syncedSession = buildSessionFromVersion(versionDataForSession, activeSessionId, activeSession?.created)
    setInitialSession(syncedSession)
    modelSessionManager.setSession(activeSessionId, syncedSession)
  }

  const resetToInitialState = () => {
    resetFormAndSession(initialFormValues, version)
  }

  const saveDatastructureVersion = async () => {
    const versionData = parseDatastructureVersionFormData(form.getValues(), isDraftMode)
    if (!versionData) {
      toast.error(tCommon('errors.formInvalid'))
      return false
    }

    try {
      const sessionDiagram = nodesWatch.length > 0 ? activeSession?.diagram || null : null
      const { model } = sessionDiagram ? buildUMLModelPayload(sessionDiagram, modelUri) : { model: null }
      const payload = mapDatastructureVersionFormToApiData(versionData, sessionDiagram, model)

      if (isCreateMode) {
        // eslint-disable-next-line unused-imports/no-unused-vars
        const { id, ...createData } = payload
        await handleCreateVersion(createData)
      } else {
        await handleUpdateVersion(payload, versionData)
      }
      return true
    } catch (error: unknown) {
      console.error('An error occurred while submitting datastructure version data.', (error as AxiosError).message)
      return false
    }
  }

  const handleSubmitValidationErrors = (errors: FieldErrors<DatastructureVersionFormData>) => {
    console.error('Validation errors: ', errors)
    toast.error(tCommon('errors.formInvalid'))
  }

  const statusHint = useMemo(() => {
    if (isInUse) return t('messages.isInUseStatusHint')
    if (isLastAvailableVersionInAvailableDatastructure) return t('messages.isLastAvailableVersion')
    return undefined
  }, [isInUse, isLastAvailableVersionInAvailableDatastructure, t])

  const dirtyFields = form.formState.dirtyFields
  const hasMetadataChanges =
    dirtyFields.version ||
    dirtyFields.description ||
    dirtyFields.dataStructureVersionSource ||
    dirtyFields.dataStructureVersionStatus
  const hasModelChanges = activeSession?.isDirty || activeSessionId !== initialSession.id
  const hasUserChanges = hasMetadataChanges || hasModelChanges

  const isConfirmButtonDisabled =
    !hasUserChanges || !!form.formState.errors.version || !!versionAlreadyExistsError || isLoading

  return {
    activeSessionId,
    canSetAvailable,
    canSetDraft,
    completedTabs,
    form,
    initialFormValues,
    initialSession,
    isConfirmButtonDisabled,
    isInUse,
    isLoading,
    modelSessionManager,
    saveDatastructureVersion,
    statusHint,
    statusWatch,
    versionAlreadyExistsError,
    handleStatusChange,
    resetToInitialState,
    handleSubmitValidationErrors,
  }
}
