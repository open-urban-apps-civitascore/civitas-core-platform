'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
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
import { useQueryParams } from '@/hooks/use-query-params'
import {
  DATASTRUCTURE_STATUS_TYPES,
  DATASTRUCTURE_VERSION_SOURCE,
  DatastructureFormAvailableSchema,
  DatastructureStatus,
  DatastructureVersionCreateData,
  DatastructureVersionFormAvailableSchema,
  DatastructureVersionFormData,
  DatastructureVersionFormDraftSchema,
  DatastructureVersionSummary,
  DatastructureVersionTab,
} from '@/types/datastructures'
import { pickDirtyValues } from '@/utils/form'

import { StructureDefinitionTab } from './components/structure-definition-tab/StructureDefinitionTab'
import { VersionInfoTab } from './components/version-info-tab/VersionInfoTab'

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
  version: DatastructureVersionSummary | null
  isCreateMode: boolean
  testId: string
}

export const VersionOverview = (props: VersionOverviewProps) => {
  const { title, datastructureId, version, isCreateMode, testId } = props
  const params = useSearchParams()
  const mode = params.get('mode')
  const t = useTranslations('datastructureVersion')
  const tCommon = useTranslations('common')
  const { setSubTabValueParam, subTabValue } = useQueryParams()
  const modelSessionManager = useMultiSessionManager({})

  const router = useRouter()

  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isReadOnly, setIsReadOnly] = useState(mode !== 'edit')

  const updateVersion = useUpdateDatastructureVersion(datastructureId)
  const createVersion = useCreateDatastructureVersion(datastructureId)
  const isLoading = updateVersion.isPending

  const initialFormValues = version || defaultFormData

  const form = useForm<DatastructureVersionFormData>({
    resolver: zodResolver(DatastructureVersionFormDraftSchema),
    mode: 'onChange',
    defaultValues: initialFormValues,
  })

  useEffect(() => {
    form.reset(initialFormValues)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version])

  const formValues = useWatch({ control: form.control })
  const descriptionWatch = form.watch('description')
  const statusWatch = form.watch('dataStructureVersionStatus')
  const versionWatch = form.watch('version')
  const sourceWatch = form.watch('dataStructureVersionSource')

  const isDraftMode = statusWatch === DATASTRUCTURE_STATUS_TYPES.DRAFT

  const hasDiagramChanges = modelSessionManager.activeSession?.isDirty

  // Allow "Available" only when the form would be valid in AVAILABLE mode
  const canSetAvailable = useMemo(() => {
    return DatastructureFormAvailableSchema.safeParse(formValues).success
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

  const handleStatusChange = (newStatus: DatastructureStatus) => {
    form.setValue('dataStructureVersionStatus', newStatus, { shouldDirty: true })
  }

  const getUmlModelData = (formValues: DatastructureVersionFormData) => {
    const diagram = modelSessionManager.activeSession?.diagram

    const diagramUpdateData = diagram ? buildUMLModelPayload(diagram) : null
    const data: DatastructureVersionCreateData = {
      ...formValues,
      model: diagramUpdateData?.model || null,
      modelName: diagramUpdateData?.name || null,
      styles: diagramUpdateData?.styles || null,
      modelAtlasUri: diagramUpdateData?.model ? `http://civitas.org/model/${datastructureId}+${versionWatch}` : null,
    }
    return data
  }

  const handleCreateVersion = () => {
    const values = form.getValues()
    const completeData = hasDiagramChanges ? getUmlModelData(values) : values

    const parsed = isDraftMode
      ? DatastructureVersionFormDraftSchema.safeParse(completeData)
      : DatastructureVersionFormAvailableSchema.safeParse(completeData)
    if (!parsed.success) {
      console.error(parsed.error)
      toast.error(tCommon('errors.formInvalid'))
      return
    }

    const { id, ...createData } = parsed.data

    createVersion.mutate(createData, {
      onSuccess: async ({ data }) => {
        toast.success(t('messages.createSuccess'))
        setIsExitModalOpen(false)
        router.push(`/datastructures/${datastructureId}/versions/${data.id}?mode=edit`)
      },
      onError: () => toast.error(tCommon('errors.unexpectedError')),
    })
  }

  const handleUpdateVersion = () => {
    const values = form.getValues()
    const updateData = getUmlModelData(values)
    const parsed = isDraftMode
      ? DatastructureVersionFormDraftSchema.safeParse(updateData)
      : DatastructureVersionFormAvailableSchema.safeParse(updateData)
    if (!parsed.success) {
      console.error(parsed.error)
      toast.error(tCommon('errors.formInvalid'))
      return
    }
    const dirtyFields = form.formState.dirtyFields
    const fieldsToUpdate = pickDirtyValues(parsed, dirtyFields)

    updateVersion.mutate(
      { ...fieldsToUpdate, id: values.id },
      {
        onSuccess: () => {
          toast.success(t('messages.updateSuccess'))
          setIsExitModalOpen(false)
          router.refresh()
        },
        onError: () => toast.error(tCommon('errors.unexpectedError')),
      },
    )
  }

  const handleSave = isCreateMode ? handleCreateVersion : handleUpdateVersion

  const handleExit = () => {
    form.reset()
    setIsReadOnly(true)
    setIsExitModalOpen(false)
  }

  const handleExitButtonClick = () => {
    if (form.formState.isDirty) setIsExitModalOpen(true)
    else handleExit()
  }

  const isConfirmButtonDisabled = useMemo(
    () =>
      !form.formState.isDirty ||
      !!form.formState.errors.version ||
      (statusWatch !== DATASTRUCTURE_STATUS_TYPES.DRAFT && Object.keys(form.formState.errors).length > 0) ||
      isLoading,
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [isLoading, statusWatch, formValues],
  )

  const ActionButtonsAndStatusSwitch = (
    <div className="flex gap-6">
      <StatusDropdown status={statusWatch} onStatusChange={handleStatusChange} canSetAvailable={canSetAvailable} />
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
        return <VersionInfoTab form={form} isReadOnly={isReadOnly} />
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
