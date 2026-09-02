'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { Resolver, useForm } from 'react-hook-form'
import { toast } from 'sonner'

import {
  useCreateDatastructureVersion,
  useStatusUpdateDatastructureVersion,
  useUpdateDatastructureVersion,
} from '@/app/services/api/datastructures/versions/clientRequests'
import { useMultiSessionManager } from '@/components/uml-modeler/hooks/use-multi-session-manager'
import { SchemaExportError } from '@/components/uml-modeler/services/jsonSchemaExportService'
import { buildUMLModelPayload } from '@/components/uml-modeler/services/modelUploadService'
import { rootFailureMessage } from '@/components/uml-modeler/services/rootFailureMessage'
import { DataStructureSchema } from '@/generated/core'
import { useError } from '@/hooks/use-error'
import { STATUS_TYPES } from '@/types/common'
import {
  DATASTRUCTURE_STATUS_TYPES,
  DATASTRUCTURE_VERSION_SOURCE,
  DatastructureStatusType,
  DatastructureVersion,
  DatastructureVersionCreateData,
  DatastructureVersionFormAvailableSchema,
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
import { buildDataStructureLogicalUrn } from '@/utils/urn'

const draftResolver: Resolver<DatastructureVersionFormData> = zodResolver(DatastructureVersionFormDraftSchema)
// The available schema narrows modelName to a non-null string, the form values stay the draft shape.
const availableResolver = zodResolver(DatastructureVersionFormAvailableSchema) as Resolver<DatastructureVersionFormData>

class ReleasedVersionUpdateError extends Error {}

const versionFormResolver: Resolver<DatastructureVersionFormData> = (values, context, options) =>
  (values.dataStructureVersionStatus === DATASTRUCTURE_STATUS_TYPES.DRAFT ? draftResolver : availableResolver)(
    values,
    context,
    options,
  )

export const defaultDatastructureVersionFormData: DatastructureVersionFormData = {
  id: '',
  version: '',
  description: '',
  dataStructureVersionSource: DATASTRUCTURE_VERSION_SOURCE.OWN,
  dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.DRAFT,
  modelName: null,
  nodes: [],
  edges: [],
}

interface UseDatastructureVersionProps {
  datastructureId: string
  dataStructureName: string
  version: DatastructureVersion | null
  isCreateMode: boolean
  onCreateVersion?: (data: DatastructureVersion) => void
}

export const useDatastructureVersion = ({
  dataStructureName,
  version,
  isCreateMode,
  onCreateVersion,
}: UseDatastructureVersionProps) => {
  const t = useTranslations('datastructureVersions')
  const tCommon = useTranslations('common')
  const tUmlModeler = useTranslations('umlModeler')
  const router = useRouter()
  const { handleFormValidationError } = useError()
  const [initialSession, setInitialSession] = useState(() => buildSessionFromVersion(version))

  const updateVersion = useUpdateDatastructureVersion()
  const createVersion = useCreateDatastructureVersion()
  const updateStatus = useStatusUpdateDatastructureVersion()

  const isLoading = updateVersion.isPending || createVersion.isPending || updateStatus.isPending

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
    resolver: versionFormResolver,
    mode: 'onChange',
    defaultValues: initialFormValues,
  })

  const statusWatch = form.watch('dataStructureVersionStatus')
  const nodesWatch = form.watch('nodes')

  const isDraftMode = statusWatch === DATASTRUCTURE_STATUS_TYPES.DRAFT

  useEffect(() => {
    form.setValue('modelName', modelName || null, { shouldDirty: shouldMarkModelFieldsDirty })
  }, [modelName, form, shouldMarkModelFieldsDirty])

  useEffect(() => {
    form.setValue('edges', edges || [], { shouldDirty: shouldMarkModelFieldsDirty })
  }, [edges, form, shouldMarkModelFieldsDirty])

  /**
   * Set form value nodes depending on the nodes amount in the diagram.
   * When there are no nodes, the diagram is considered not existing.
   */
  useEffect(() => {
    form.setValue('nodes', nodes || [], { shouldDirty: shouldMarkModelFieldsDirty })
  }, [nodes, form, shouldMarkModelFieldsDirty])

  /**
   * after closing the diagram, a new session gets created. This session is clean.
   * Therefore, it has to be checked if it is still the same session, based on the session id.
   * Also, set all model form values to null after closing the session.
   */
  useEffect(() => {
    if (!activeSession?.isDirty && activeSessionId !== initialSession?.id) {
      form.setValue('modelName', null, { shouldDirty: true })
      form.setValue('nodes', [], { shouldDirty: true })
      form.setValue('edges', [], { shouldDirty: true })
    }
  }, [activeSession, initialSession, form, activeSessionId])

  useEffect(() => {
    if (isDraftMode) {
      form.clearErrors()
      return
    }
    void form.trigger()
  }, [form, isDraftMode])

  const handleStatusChange = (newStatus: DatastructureStatusType) => {
    form.setValue('dataStructureVersionStatus', newStatus, { shouldDirty: true })
  }

  const handleStatusUpdate = async (
    versionId: string,
    endpoint: 'release' | 'unrelease',
    datastructureId: string,
  ): Promise<DatastructureVersion> => {
    try {
      const response = await updateStatus.mutateAsync({
        data: undefined,
        endpoint: `/datastructures/${datastructureId}/versions/${versionId}/${endpoint}`,
      })
      toast.success(tCommon('success.statusChangeSuccess'))
      router.refresh()
      return response.data
    } catch (error) {
      toast.error(tCommon('errors.statusChangeError'))
      throw error
    }
  }

  const handleCreateVersion = async (createData: DatastructureVersionCreateData, datastructureId: string) => {
    const isStatusFieldDirty = form.formState.dirtyFields.dataStructureVersionStatus
    const shouldRelease = !!isStatusFieldDirty && statusWatch === DATASTRUCTURE_STATUS_TYPES.AVAILABLE

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

    if (shouldRelease) await handleStatusUpdate(data.id, 'release', datastructureId)

    onCreateVersion?.(data)
  }

  const handleUpdateValues = async (
    values: DatastructureVersionPutData,
    datastructureId: string,
  ): Promise<DatastructureVersion> => {
    // An available version accepts nothing but a status change, so no field can reach an update.
    if (initialFormValues.dataStructureVersionStatus === STATUS_TYPES.AVAILABLE) {
      toast.error(t('messages.isAvailableModelHint'))
      throw new ReleasedVersionUpdateError()
    }

    try {
      const response = await updateVersion.mutateAsync({
        data: values,
        endpoint: `/datastructures/${datastructureId}/versions/${values.id}`,
      })
      toast.success(t('messages.updateSuccess'))
      // Storing a model assigns the version its number, and the heading is rendered on the server.
      router.refresh()
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
    const shouldRelease =
      !!dirtyFields.dataStructureVersionStatus && statusWatch === DATASTRUCTURE_STATUS_TYPES.AVAILABLE
    const shouldUnrelease = !!dirtyFields.dataStructureVersionStatus && statusWatch === DATASTRUCTURE_STATUS_TYPES.DRAFT

    let versionResponse: DatastructureVersion | null = shouldUpdateValues
      ? await handleUpdateValues(parsedPayload, datastructureId)
      : null
    if (shouldRelease) versionResponse = await handleStatusUpdate(parsedPayload.id, 'release', datastructureId)
    if (shouldUnrelease) versionResponse = await handleStatusUpdate(parsedPayload.id, 'unrelease', datastructureId)

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
    const parsed = parseDatastructureVersionFormData(form.getValues(), isDraftMode)
    if (!parsed.success) {
      handleFormValidationError(parsed.error)
      return false
    }

    try {
      const sessionDiagram = nodesWatch.length > 0 ? activeSession?.diagram || null : null
      // The model document IS the DataStructure: its $id is the DataStructure URN. Model Forge folds
      // the wrapper into a single DataStructure artifact (carrying the shape + styles) that CONTAINS
      // its member Elements (the $defs classes) — with no separate root Element. Mappings and sinks
      // pin a version of this same identity, so the model carries the logical form: the registry
      // assigns the version on store, and one authored here would only be a guess.
      const modelUri = sessionDiagram ? buildDataStructureLogicalUrn(dataStructureName, datastructureId) : undefined
      let model: Record<string, unknown> | null = null
      if (sessionDiagram) {
        try {
          model = buildUMLModelPayload(sessionDiagram, modelUri).model
        } catch (error) {
          if (!(error instanceof SchemaExportError)) throw error
          // No version is saved without a model, draft included: a version whose diagram never
          // became a schema is invisible until release, and the deploy engine reads the model. The
          // failure is actionable — designate a root, connect the stray element — so the save is
          // refused with that reason rather than silently persisting the diagram alone.
          const reason = rootFailureMessage(tUmlModeler, error.failure)
          toast.error(t('errors.saveInvalidModel', { reason }))
          return false
        }
        // Validate the assembled CORE DataStructure document against the generated schema before
        // sending — the frontend guarantees a schema-valid payload to the (schema-agnostic) backend,
        // mirroring the pipeline/mapping/datasource editors. Model Forge splits the $defs members into
        // Element artifacts on ingest; the host only ever stores the envelope.
        if (model) {
          const validated = DataStructureSchema.safeParse(model)
          if (!validated.success) {
            console.error('DataStructure model failed CORE schema validation:', validated.error.issues, model)
            toast.error(tCommon('errors.unexpectedError'))
            return false
          }
        }
      }
      const payload = mapDatastructureVersionFormToApiData(parsed.data, sessionDiagram, model)

      if (isCreateMode) {
        // eslint-disable-next-line unused-imports/no-unused-vars
        const { id, ...createData } = payload
        await handleCreateVersion(createData, datastructureId)
      } else {
        await handleUpdateVersion(payload, parsed.data, datastructureId)
      }
      return true
    } catch (error: unknown) {
      if (error instanceof ReleasedVersionUpdateError) return false
      console.error('An error occurred while submitting datastructure version data.', error)
      toast.error(tCommon('errors.unexpectedError'))
      return false
    }
  }

  const dirtyFields = form.formState.dirtyFields
  const hasMetadataChanges =
    dirtyFields.description || dirtyFields.dataStructureVersionSource || dirtyFields.dataStructureVersionStatus
  const hasModelChanges =
    activeSession?.isDirty === true || (!!initialSession?.id && activeSessionId !== initialSession?.id)
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
  }
}
