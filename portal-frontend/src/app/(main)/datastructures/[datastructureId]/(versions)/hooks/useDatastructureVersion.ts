'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { AxiosError } from 'axios'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { FieldErrors, useForm } from 'react-hook-form'
import { toast } from 'sonner'

import {
  useCreateDatastructureVersion,
  useStatusUpdateDatastructureVersion,
  useUpdateDatastructureVersion,
  useUpdateDatastructureVersionPublished,
} from '@/app/services/api/datastructures/versions/clientRequests'
import { useMultiSessionManager } from '@/components/uml-modeler/hooks/use-multi-session-manager'
import { buildUMLModelPayload } from '@/components/uml-modeler/services/modelUploadService'
import { STATUS_TYPES } from '@/types/common'
import {
  DATASTRUCTURE_STATUS_TYPES,
  DATASTRUCTURE_VERSION_SOURCE,
  DatastructureStatusTypes,
  DatastructureVersion,
  DatastructureVersionCreateData,
  DatastructureVersionFormData,
  DatastructureVersionFormDraftSchema,
  DatastructureVersionPutData,
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

interface UseDatastructureVersionProps {
  datastructureId: string
  version: DatastructureVersion | null
  isCreateMode: boolean
  onCreateVersion?: (data: DatastructureVersion) => void
  canSetAvailable?: boolean
}

export const useDatastructureVersion = ({
  datastructureId,
  version,
  isCreateMode,
  onCreateVersion,
  canSetAvailable = true,
}: UseDatastructureVersionProps) => {
  const t = useTranslations('datastructureVersions')
  const tCommon = useTranslations('common')
  const [initialSession, setInitialSession] = useState(() => buildSessionFromVersion(version))

  const updateVersion = useUpdateDatastructureVersion()
  const updatePublishedVersion = useUpdateDatastructureVersionPublished()
  const createVersion = useCreateDatastructureVersion()
  const updateStatus = useStatusUpdateDatastructureVersion()

  const isLoading =
    updateVersion.isPending || createVersion.isPending || updateStatus.isPending || updatePublishedVersion.isPending

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

  const statusWatch = form.watch('dataStructureVersionStatus')
  const nodesWatch = form.watch('nodes')
  const versionWatch = form.watch('version')

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

  const handleStatusChange = (newStatus: DatastructureStatusTypes) => {
    form.setValue('dataStructureVersionStatus', newStatus, { shouldDirty: true })
  }

  const handleStatusUpdate = async (
    versionId: string,
    endpoint: 'publish' | 'unpublish',
    datastructureId: string,
  ): Promise<DatastructureVersion> => {
    try {
      const response = await updateStatus.mutateAsync({
        data: undefined,
        endpoint: `/datastructures/${datastructureId}/versions/${versionId}/${endpoint}`,
      })
      toast.success(tCommon('success.statusChangeSuccess'))
      return response.data
    } catch (error) {
      toast.error(tCommon('errors.statusChangeError'))
      throw error
    }
  }

  const handleCreateVersion = async (createData: DatastructureVersionCreateData, datastructureId: string) => {
    const isStatusFieldDirty = form.formState.dirtyFields.dataStructureVersionStatus
    const shouldPublish = !!isStatusFieldDirty && statusWatch === DATASTRUCTURE_STATUS_TYPES.AVAILABLE

    let data: DatastructureVersion
    try {
      const response = await createVersion.mutateAsync({
        data: createData,
        endpoint: `/datastructures/${datastructureId}/versions`,
      })
      data = response.data
    } catch (error) {
      toast.error(t('errors.creationError'))
      throw error
    }

    toast.success(t('messages.createSuccess'))

    if (shouldPublish) await handleStatusUpdate(data.id, 'publish', datastructureId)

    onCreateVersion?.(data)
  }

  const handleUpdateValues = async (
    values: DatastructureVersionPutData,
    datastructureId: string,
  ): Promise<DatastructureVersion> => {
    try {
      let response: { data: DatastructureVersion }
      if (initialFormValues.dataStructureVersionStatus === STATUS_TYPES.AVAILABLE)
        response = await updatePublishedVersion.mutateAsync({
          data: values,
          endpoint: `/datastructures/${datastructureId}/versions/${values.id}/published/meta`,
        })
      else
        response = await updateVersion.mutateAsync({
          data: values,
          endpoint: `/datastructures/${datastructureId}/versions/${values.id}`,
        })
      toast.success(t('messages.updateSuccess'))
      return response.data
    } catch (error) {
      toast.error(tCommon('errors.updateError', { item: tCommon('items.datastructureVersion') }))
      throw error
    }
  }

  const handleUpdateVersion = async (
    parsedPayload: DatastructureVersionPutData,
    parsedFormValues: DatastructureVersionFormData,
    datastructureId: string,
  ) => {
    const formValues = form.getValues()
    const dirtyFields = form.formState.dirtyFields
    const fieldsToUpdate = pickDirtyValues(formValues, dirtyFields)

    const shouldUpdateValues = containsNonStatusField(fieldsToUpdate)
    const shouldPublish =
      !!dirtyFields.dataStructureVersionStatus && statusWatch === DATASTRUCTURE_STATUS_TYPES.AVAILABLE
    const shouldUnpublish = !!dirtyFields.dataStructureVersionStatus && statusWatch === DATASTRUCTURE_STATUS_TYPES.DRAFT

    let versionResponse: DatastructureVersion | null = shouldUpdateValues
      ? await handleUpdateValues(parsedPayload, datastructureId)
      : null
    if (shouldPublish) versionResponse = await handleStatusUpdate(parsedPayload.id, 'publish', datastructureId)
    if (shouldUnpublish) versionResponse = await handleStatusUpdate(parsedPayload.id, 'unpublish', datastructureId)

    const updatedFormValues = versionResponse ? mapDatastructureVersionApiToFormData(versionResponse) : parsedFormValues

    resetFormAndSession(updatedFormValues, versionResponse || version)
  }

  const resetSession = (version: DatastructureVersion | null, shouldMarkDirty = false) => {
    if (!activeSessionId) return
    const syncedSession = buildSessionFromVersion(version, activeSessionId, activeSession?.created)
    modelSessionManager.setSession(activeSessionId, syncedSession)
    if (shouldMarkDirty) modelSessionManager.markSessionDirty(activeSessionId, 'model')
    return syncedSession
  }

  const resetFormAndSession = (
    formValuesToReset: DatastructureVersionFormData,
    version: DatastructureVersion | null,
  ) => {
    form.reset(formValuesToReset)
    const syncedSession = resetSession(version)
    if (syncedSession) setInitialSession(syncedSession)
  }

  const resetToInitialState = () => {
    resetFormAndSession(initialFormValues, version)
  }

  const saveDatastructureVersion = async (datastructureId: string) => {
    const formData = parseDatastructureVersionFormData(form.getValues(), isDraftMode)
    if (!formData) {
      toast.error(tCommon('errors.formInvalid'))
      return false
    }

    try {
      const sessionDiagram = nodesWatch.length > 0 ? activeSession?.diagram || null : null
      const { model } = sessionDiagram ? buildUMLModelPayload(sessionDiagram, modelUri) : { model: null }
      const payload = mapDatastructureVersionFormToApiData(formData, sessionDiagram, model)

      if (isCreateMode) {
        // eslint-disable-next-line unused-imports/no-unused-vars
        const { id, ...createData } = payload
        await handleCreateVersion(createData, datastructureId)
      } else {
        await handleUpdateVersion(payload, formData, datastructureId)
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

  const dirtyFields = form.formState.dirtyFields
  const hasMetadataChanges =
    dirtyFields.version ||
    dirtyFields.description ||
    dirtyFields.dataStructureVersionSource ||
    dirtyFields.dataStructureVersionStatus
  const hasModelChanges = activeSession?.isDirty || activeSessionId !== initialSession.id
  const hasUserChanges = hasMetadataChanges || hasModelChanges

  return {
    activeSessionId,
    form,
    initialFormValues,
    initialSession,
    isLoading,
    modelSessionManager,
    saveDatastructureVersion,
    handleStatusChange,
    resetToInitialState,
    hasUserChanges,
    resetSession,
    resetFormAndSession,
    handleSubmitValidationErrors,
  }
}
