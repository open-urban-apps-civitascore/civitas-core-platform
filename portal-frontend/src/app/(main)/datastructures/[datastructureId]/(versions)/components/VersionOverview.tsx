'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import {
  useCreateDatastructureVersion,
  useUpdateDatastructureVersion,
} from '@/app/services/api/datastructures/versions/clientRequests'
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
import { importFromXmi } from '@/components/uml-modeler/services/xmiImportService'
import { DirtyField } from '@/components/uml-modeler/types/session'
import { useQueryParams } from '@/hooks/use-query-params'
import {
  DATASTRUCTURE_STATUS_TYPES,
  DATASTRUCTURE_VERSION_SOURCE,
  DatastructureStatusTypes,
  DatastructureVersion,
  DatastructureVersionFormAvailableSchema,
  DatastructureVersionFormData,
  DatastructureVersionFormDraftSchema,
  DatastructureVersionTab,
} from '@/types/datastructures'
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
  model: null,
  styles: null,
}

const tabs: Tab<DatastructureVersionTab>[] = [
  {
    value: 'structure',
    label: 'datastructureVersion.tabs.structure',
  },
  {
    value: 'versionInfo',
    label: 'datastructureVersion.tabs.versionInfo',
  },
]

const DEFAULT_TAB = tabs[0]

interface VersionOverviewProps {
  title: string
  datastructureId: string
  version: DatastructureVersion | null
  isCreateMode: boolean
  testId: string
  existingVersions: { id: string; version: string }[]
}

export const VersionOverview = (props: VersionOverviewProps) => {
  const { title, datastructureId, version, isCreateMode, testId, existingVersions } = props
  const params = useSearchParams()
  const mode = params.get('mode')
  const t = useTranslations('datastructureVersion')
  const tCommon = useTranslations('common')
  const { setSubTabValueParam, subTabValue } = useQueryParams()
  const initialFormValues = useRef<DatastructureVersionFormData>(
    version ? { ...version, description: version?.description || '' } : defaultFormData,
  )
  const initialSession = useMemo(() => {
    const values = initialFormValues.current
    const importResult = importFromXmi(values?.model || '')
    const diagram = importResult.diagram
    if (!diagram && !values?.modelName) return undefined
    if (!diagram) return createEmptySession(values.modelName as string)
    return {
      id: diagram.id,
      name: values?.modelName || 'Untitled Diagram',
      diagram: diagram,
      isDirty: false,
      dirtyFields: new Set<DirtyField>(),
      lastModified: diagram.lastModified,
      created: diagram.lastModified,
    }
  }, [initialFormValues])

  const modelSessionManager = useMultiSessionManager({ initialSession })

  const router = useRouter()

  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isReadOnly, setIsReadOnly] = useState(mode !== 'edit')

  const updateVersion = useUpdateDatastructureVersion(datastructureId)
  const createVersion = useCreateDatastructureVersion(datastructureId)
  const isLoading = updateVersion.isPending

  const form = useForm<DatastructureVersionFormData>({
    resolver: zodResolver(DatastructureVersionFormDraftSchema),
    mode: 'onChange',
    defaultValues: initialFormValues.current,
  })

  const dirtyModelFields = modelSessionManager.activeSession?.dirtyFields

  const formValues = useWatch({ control: form.control })
  const descriptionWatch = form.watch('description')
  const statusWatch = form.watch('dataStructureVersionStatus')
  const versionWatch = form.watch('version')
  const modelWatch = form.watch('model')
  const sourceWatch = form.watch('dataStructureVersionSource')

  const isDraftMode = statusWatch === DATASTRUCTURE_STATUS_TYPES.DRAFT

  const isDiagramDirty = modelSessionManager.activeSession?.isDirty

  const versionAlreadyExistsError = useMemo(() => {
    const versionExists = existingVersions.find(
      version => version.version === versionWatch.trim() && version.id !== initialFormValues.current.id,
    )
    if (versionExists) {
      return t('errors.versionAlreadyExists')
    } else {
      return undefined
    }
  }, [existingVersions, versionWatch, t])

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
    if (versionWatch.length > 0 && descriptionWatch.length > 0 && sourceWatch) {
      completed.push('versionInfo')
    }
    return completed
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [formValues])

  const handleStatusChange = (newStatus: DatastructureStatusTypes) => {
    form.setValue('dataStructureVersionStatus', newStatus, { shouldDirty: true })
  }

  useEffect(() => {
    const diagram = modelSessionManager.activeSession?.diagram
    const hasNameChanges = dirtyModelFields?.has('modelName')
    const hasModelChanges = dirtyModelFields?.has('model')
    const diagramUpdateData = diagram ? buildUMLModelPayload(diagram) : null
    if (hasNameChanges && diagramUpdateData) {
      form.setValue('modelName', diagramUpdateData.name, { shouldDirty: true })
    }
    if (hasModelChanges && diagramUpdateData) {
      form.setValue('model', diagramUpdateData.model, { shouldDirty: true })
      form.setValue('styles', diagramUpdateData.styles, { shouldDirty: true })
      form.setValue('modelAtlasUri', `http://civitas.org/model/${datastructureId}+${versionWatch}`, {
        shouldDirty: true,
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dirtyModelFields, versionWatch])

  useEffect(() => {
    if (versionWatch && modelWatch) {
      form.setValue('modelAtlasUri', `http://civitas.org/model/${datastructureId}/${versionWatch}`, {
        shouldDirty: true,
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [versionWatch, modelWatch])

  const handleCreateVersion = () => {
    const values = form.getValues()
    const parsed = isDraftMode
      ? DatastructureVersionFormDraftSchema.safeParse(values)
      : DatastructureVersionFormAvailableSchema.safeParse(values)
    if (!parsed.success) {
      console.error(parsed.error)
      toast.error(tCommon('errors.formInvalid'))
      return
    }

    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...createData } = parsed.data

    createVersion.mutate(createData, {
      onSuccess: async ({ data }) => {
        toast.success(t('messages.createSuccess'))
        setIsExitModalOpen(false)
        router.push(`/datastructures/${datastructureId}/${data.id}?mode=edit`)
      },
      onError: () => {
        toast.error(tCommon('errors.unexpectedError'))
        setIsExitModalOpen(false)
      },
    })
  }

  const handleUpdateVersion = () => {
    const values = form.getValues()
    const parsed = isDraftMode
      ? DatastructureVersionFormDraftSchema.safeParse(values)
      : DatastructureVersionFormAvailableSchema.safeParse(values)
    if (!parsed.success) {
      console.error(parsed.error)
      toast.error(tCommon('errors.formInvalid'))
      return
    }
    const dirtyFields = form.formState.dirtyFields
    const fieldsToUpdate = pickDirtyValues(parsed.data, dirtyFields)

    updateVersion.mutate(
      { ...fieldsToUpdate, id: values.id },
      {
        onSuccess: () => {
          toast.success(t('messages.updateSuccess'))
          setIsExitModalOpen(false)
          router.refresh()
        },
        onError: () => {
          toast.error(tCommon('errors.unexpectedError'))
          setIsExitModalOpen(false)
        },
      },
    )
  }

  const handleSave = isCreateMode ? handleCreateVersion : handleUpdateVersion

  const handleExit = () => {
    if (modelSessionManager.activeSessionId) {
      modelSessionManager.closeSession(
        modelSessionManager.activeSessionId,
        initialFormValues.current.modelName || undefined,
      )
    }
    form.reset(initialFormValues.current)
    setIsReadOnly(true)
    setIsExitModalOpen(false)
  }

  const handleExitButtonClick = () => {
    if (form.formState.isDirty) setIsExitModalOpen(true)
    else handleExit()
  }

  const isConfirmButtonDisabled = useMemo(
    () =>
      (!form.formState.isDirty && !isDiagramDirty) ||
      !!form.formState.errors.version ||
      !!versionAlreadyExistsError ||
      isLoading,
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [isLoading, statusWatch, formValues, isDiagramDirty],
  )

  const ActionButtonsAndStatusSwitch = (
    <div className="flex gap-6">
      <StatusDropdown
        statusOptions={Object.values(DATASTRUCTURE_STATUS_TYPES)}
        status={statusWatch}
        onStatusChange={handleStatusChange}
        canSetAvailable={canSetAvailable}
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
        return <StructureDefinitionTab isReadOnly={isReadOnly} modelSessionManager={modelSessionManager} />
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
      {isLoading ? <LoadingSpinner className="h-[300px]" /> : renderTabContent()}

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
