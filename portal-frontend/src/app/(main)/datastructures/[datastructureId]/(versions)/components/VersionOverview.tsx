'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { UseMutationResult } from '@tanstack/react-query'
import { AxiosError } from 'axios'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import {
  useCreateDatastructureVersion,
  usePublishDatastructureVersion,
  useUnpublishDatastructureVersion,
  useUpdateDatastructureVersion,
  useUpdateDatastructureVersionPublished,
} from '@/app/services/api/datastructures/versions/clientRequests'
import { ApiServiceResponse } from '@/app/services/api/request/apiRequest'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { StatusDropdown } from '@/components/status-dropdown/StatusDropdown'
import { Button } from '@/components/ui/button'
import { useMultiSessionManager } from '@/components/uml-modeler/hooks/use-multi-session-manager'
import { buildUMLModelPayload } from '@/components/uml-modeler/services/modelUploadService'
import { createEmptySession } from '@/components/uml-modeler/services/sessionService'
import { DirtyField } from '@/components/uml-modeler/types/session'
import { QUERY_PARAMS } from '@/const/searchParams'
import { useQueryParams } from '@/hooks/use-query-params'
import { STATUS_TYPES, WithId } from '@/types/common'
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
  mapDatastructureVersionApiToFormData,
  mapDatastructureVersionFormToApiData,
  parseDatastructureVersionFormData,
} from '@/utils/datastructures'
import { pickDirtyValues } from '@/utils/form'

import { StructureDefinitionTab } from './structure-definition-tab/StructureDefinitionTab'
import { VersionInfoTab } from './version-info-tab/VersionInfoTab'

export const defaultFormData: DatastructureVersionFormData = {
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

const tabs: Tab<DatastructureVersionTab>[] = [
  {
    value: 'structure',
    label: 'datastructureVersions.tabs.structure',
  },
  {
    value: 'versionInfo',
    label: 'datastructureVersions.tabs.versionInfo',
  },
]

const DEFAULT_TAB = tabs[0]

interface VersionOverviewProps {
  title: string
  datastructureId: string
  version: DatastructureVersion | null
  isCreateMode: boolean
  testId: string
  otherVersions: { id: string; version: string; dataStructureVersionStatus: DatastructureStatusTypes }[]
  isDatastructureAvailable: boolean
}

export const VersionOverview = (props: VersionOverviewProps) => {
  const { title, datastructureId, version, isCreateMode, testId, otherVersions, isDatastructureAvailable } = props
  const params = useSearchParams()
  const mode = params.get('mode')
  const router = useRouter()
  const t = useTranslations('datastructureVersions')
  const tCommon = useTranslations('common')
  const { setSubTabValueParam, subTabValue } = useQueryParams()

  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isReadOnly, setIsReadOnly] = useState(mode !== 'edit')

  const updateVersion = useUpdateDatastructureVersion(datastructureId)
  const updatePublishedVersion = useUpdateDatastructureVersionPublished(datastructureId)
  const createVersion = useCreateDatastructureVersion(datastructureId)
  const publishVersion = usePublishDatastructureVersion(datastructureId)
  const unpublishVersion = useUnpublishDatastructureVersion(datastructureId)

  const isLoading =
    updateVersion.isPending || createVersion.isPending || publishVersion.isPending || unpublishVersion.isPending

  const initialSession = useMemo(() => {
    const diagram = version?.styles || null
    const modelName = version?.modelName || null

    if (!diagram) return createEmptySession(modelName || undefined)
    return {
      id: diagram.id,
      name: modelName || 'Untitled Diagram',
      diagram,
      isDirty: false,
      dirtyFields: new Set<DirtyField>(),
      lastModified: diagram.lastModified,
      created: diagram.lastModified,
    }
  }, [version])

  const modelSessionManager = useMultiSessionManager({ initialSession })
  const nodes = modelSessionManager.activeSession?.diagram.nodes
  const edges = modelSessionManager.activeSession?.diagram.edges
  const modelName = modelSessionManager.activeSession?.diagram.name
  const activeSession = modelSessionManager.activeSession
  const activeSessionId = modelSessionManager.activeSessionId

  const initialFormValues = useMemo(
    () => (version ? mapDatastructureVersionApiToFormData(version) : defaultFormData),
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
    form.setValue('modelName', modelName || null, { shouldDirty: true })
  }, [modelName, form])

  useEffect(() => {
    form.setValue('edges', edges || [], { shouldDirty: true })
  }, [edges, form])

  /**
   * Set form values nodes and modelAtlasUri depending on the nodes amount in the diagram
   * If there are no nodes, the diagram is considered not existing, and modelAtlasUri gets set to null
   */
  useEffect(() => {
    form.setValue('nodes', nodes || [], { shouldDirty: true })
    const hasDiagram = nodes && nodes.length > 0
    const modelAtlasUriValue = hasDiagram ? modelUri : null
    form.setValue('modelAtlasUri', modelAtlasUriValue, {
      shouldDirty: modelAtlasUriValue !== initialFormValues.modelAtlasUri,
    })
  }, [nodes, form, modelUri, initialFormValues.modelAtlasUri])

  /**
   * Set setIsDiagramDirty when active session changes:
   * after closing the diagram, a new session gets created. This session is clean.
   * Therefore, it has to be checked if it is still the same session, based on the session id
   *
   *  Also, set all model form values to null after closing the session
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
    const versionExists = otherVersions.find(version => version.version === versionWatch.trim())
    if (versionExists) {
      return t('errors.versionAlreadyExists')
    } else {
      return undefined
    }
  }, [otherVersions, versionWatch, t])

  // A version that is in use can not be set back to draft
  const isInUse = version?.inUse

  // The last available version in an available datastructure can not be set back to draft
  const isLastAvailableVersionInAvailableDatastructure =
    isDatastructureAvailable &&
    !otherVersions.some(version => version.dataStructureVersionStatus === DATASTRUCTURE_STATUS_TYPES.AVAILABLE)

  const canSetDraft = !isInUse && !isLastAvailableVersionInAvailableDatastructure

  // Allow "Available" only when the form would be valid in AVAILABLE mode
  const canSetAvailable = useMemo(() => {
    return DatastructureVersionFormAvailableSchema.safeParse(formValues).success
  }, [formValues])

  const revalidateForm = () => {
    if (!isDraftMode) {
      void form.trigger()
    }
  }
  // Auto-revert status to draft when required fields become empty
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
    mutationFn: UseMutationResult<ApiServiceResponse<DatastructureVersion>, unknown, WithId, unknown>,
    versionId: string,
  ) => {
    try {
      await mutationFn.mutateAsync({ id: versionId })
      toast.success(tCommon('success.statusChangeSuccess'))
    } catch (error) {
      toast.error(tCommon('errors.statusChangeError'))
      throw error
    }
  }

  const handleCreateVersion = (createData: DatastructureVersionCreateData) => {
    const isStausfieldDirty = form.formState.dirtyFields.dataStructureVersionStatus
    const shouldPublish = !!isStausfieldDirty && statusWatch === DATASTRUCTURE_STATUS_TYPES.AVAILABLE
    setIsExitModalOpen(false)
    createVersion.mutate(createData, {
      onSuccess: async ({ data }) => {
        toast.success(t('messages.createSuccess'))
        if (shouldPublish) {
          try {
            await handleStatusUpdate(publishVersion, data.id)
            router.push(
              `/datastructures/${datastructureId}/${data.id}?mode=edit&${QUERY_PARAMS.subTabValue}=${subTabValue}`,
            )
          } catch (error) {
            console.error(error)
          }
        } else {
          router.push(
            `/datastructures/${datastructureId}/${data.id}?mode=edit&${QUERY_PARAMS.subTabValue}=${subTabValue}`,
          )
        }
      },
      onError: () => {
        toast.error(t('errors.creationError'))
      },
    })
  }

  const resetValues = () => {
    const currentValues = form.getValues()
    form.reset(currentValues)
  }

  const handleUpdateValues = async (values: DatastructureVersionPutData) => {
    try {
      if (initialFormValues.dataStructureVersionStatus === STATUS_TYPES.AVAILABLE)
        await updatePublishedVersion.mutateAsync({ ...values, id: values.id })
      else await updateVersion.mutateAsync({ ...values, id: values.id })
      toast.success(t('messages.updateSuccess'))
    } catch (error) {
      toast.error(tCommon('errors.updateError', { item: tCommon('items.datastructureVersion') }))
      throw error
    }
  }

  const handleUpdateVersion = async (parsedValues: DatastructureVersionPutData) => {
    try {
      const dirtyFields = form.formState.dirtyFields

      const shouldPublish =
        !!dirtyFields.dataStructureVersionStatus && statusWatch === DATASTRUCTURE_STATUS_TYPES.AVAILABLE
      const shouldUnpublish =
        !!dirtyFields.dataStructureVersionStatus && statusWatch === DATASTRUCTURE_STATUS_TYPES.DRAFT

      const fieldsToUpdate = pickDirtyValues(formValues, dirtyFields)
      const shouldUpdateValues = (Object.keys(fieldsToUpdate) as (keyof DatastructureVersionFormData)[]).some(
        key => key !== 'dataStructureVersionStatus',
      )
      setIsExitModalOpen(false)
      if (shouldUpdateValues) await handleUpdateValues(parsedValues)

      if (shouldPublish) {
        await handleStatusUpdate(publishVersion, parsedValues.id)
      }
      if (shouldUnpublish) {
        await handleStatusUpdate(unpublishVersion, parsedValues.id)
      }
      resetValues()
      router.refresh()
    } catch (error: unknown) {
      console.error('An error occurred while submitting datastructure version data.', (error as AxiosError).message)
    }
  }

  const handleSave = async () => {
    const versionData = parseDatastructureVersionFormData(form.getValues(), isDraftMode)
    if (!versionData) {
      toast.error(tCommon('errors.formInvalid'))
      return
    }

    const sessionDiagram = nodesWatch.length > 0 ? activeSession?.diagram || null : null
    const { model } = sessionDiagram ? buildUMLModelPayload(sessionDiagram, modelUri) : { model: null }
    const payload = mapDatastructureVersionFormToApiData(versionData, sessionDiagram, model)
    if (isCreateMode) {
      // eslint-disable-next-line unused-imports/no-unused-vars
      const { id, ...createData } = payload
      handleCreateVersion(createData)
    } else {
      await handleUpdateVersion(payload)
    }
  }

  const handleExit = () => {
    if (activeSessionId) {
      modelSessionManager.setSession(activeSessionId, initialSession)
    }
    form.reset(initialFormValues)
    setIsReadOnly(true)
    setIsExitModalOpen(false)
  }

  const handleExitButtonClick = () => {
    if (form.formState.isDirty) setIsExitModalOpen(true)
    else handleExit()
  }

  const isConfirmButtonDisabled = useMemo(
    () => {
      return !form.formState.isDirty || !!form.formState.errors.version || !!versionAlreadyExistsError || isLoading
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [isLoading, statusWatch, formValues],
  )

  const statusHint = useMemo(() => {
    if (isInUse) return t('messages.isInUseStatusHint')
    else if (isLastAvailableVersionInAvailableDatastructure) return t('messages.isLastAvailableVersion')
  }, [isInUse, isLastAvailableVersionInAvailableDatastructure, t])

  const ActionButtonsAndStatusSwitch = (
    <div className="flex gap-6">
      <StatusDropdown
        statusOptions={Object.values(DATASTRUCTURE_STATUS_TYPES)}
        status={statusWatch}
        onStatusChange={handleStatusChange}
        canSetAvailable={canSetAvailable}
        canSetDraft={canSetDraft}
        statusHint={statusHint}
      />
      <ActionButtons
        confirmButtonType="button"
        onCancelClick={handleExitButtonClick}
        onConfirmClick={handleSave}
        isConfirmButtonDisabled={isConfirmButtonDisabled}
        isCancelButtonDisabled={isLoading}
        cancelButtonTitle={tCommon('actions.exit')}
        hasCard={false}
        wrapperClassname="w-auto"
      />
    </div>
  )

  const EditButton = (
    <Button data-testid="editButton" type="button" onClick={() => setIsReadOnly(false)}>
      {tCommon('actions.edit')}
    </Button>
  )

  const renderTabContent = () => {
    switch (subTabValue) {
      case 'versionInfo':
        return (
          <VersionInfoTab form={form} isReadOnly={isReadOnly} versionAlreadyExistsError={versionAlreadyExistsError} />
        )
      case 'structure':
      default:
        return (
          <StructureDefinitionTab
            isReadOnly={isReadOnly}
            modelSessionManager={modelSessionManager}
            isInUse={version?.inUse || false}
          />
        )
    }
  }

  return (
    <PageContainer testId={testId} headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader
        title={title}
        segmentedControlBarProps={{
          tabs: tabs,
          selectedTab: subTabValue || DEFAULT_TAB.value,
          onTabChange: selectedTab => setSubTabValueParam(selectedTab),
          completedTabs,
          hasCompletionStatus: true,
        }}
        customElement={isReadOnly ? EditButton : ActionButtonsAndStatusSwitch}
      />
      {isLoading ? <LoadingSpinner className="h-full" /> : renderTabContent()}

      <ExitWarningModal
        open={isExitModalOpen}
        onOpenChange={() => setIsExitModalOpen(false)}
        onDiscard={handleExit}
        onConfirm={handleSave}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
