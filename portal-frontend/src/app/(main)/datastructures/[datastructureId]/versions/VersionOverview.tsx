'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslations } from 'next-intl'
import { useRouter, useSearchParams } from 'next/navigation'
import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ExitWarningModal } from '@/components/exit-warning-modal/ExitWarningModal'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { StatusDropdown } from '@/components/status-dropdown/StatusDropdown'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { Status, STATUS_TYPES } from '@/types/common'
import {
  Datastructure,
  DatastructureFormAvailableSchema,
  DatastructureVersionFormAvailableSchema,
  DatastructureVersionFormData,
  DatastructureVersionFormDraftSchema,
  DatastructureVersionSummary,
  DatastructureVersionTab,
  SOURCE,
} from '@/types/datastructures'
import {
  useCreateDatastructureVersion,
  useUpdateDatastructureVersion,
} from '@/app/services/api/datastructures/versions/clientRequests'

export const defaultVersion: DatastructureVersionSummary = {
  id: '',
  versionNumber: '',
  description: '',
  source: SOURCE.OWN,
  status: STATUS_TYPES.DRAFT,
}

const tabs: Tab<DatastructureVersionTab>[] = [
  {
    value: 'structure',
    label: 'datastructureVersions.tabs.basicInfo',
  },
  {
    value: 'versionInfo',
    label: 'datastructureVersionss.tabs.versions',
  },
]

interface VersionOverviewProps {
  title: string
  datastructureId: string
  versionId: string
  version: DatastructureVersionSummary
  isCreateMode: boolean
  testId: string
}

export const VersionOverview = (props: VersionOverviewProps) => {
  const { title, datastructureId, version, isCreateMode, testId } = props
  const params = useSearchParams()
  const mode = params.get('mode')
  const t = useTranslations('datastructureVersions')
  const tCommon = useTranslations('common')

  const router = useRouter()

  const [selectedTab, setSelectedTab] = useState<DatastructureVersionTab>('structure')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [isReadOnly, setIsReadOnly] = useState(mode !== 'edit')

  const updateVersion = useUpdateDatastructureVersion(datastructureId)
  const createVersion = useCreateDatastructureVersion(datastructureId)
  const isLoading = updateVersion.isPending

  const form = useForm<DatastructureVersionFormData>({
    resolver: zodResolver(DatastructureVersionFormDraftSchema),
    mode: 'onChange',
    defaultValues: version,
  })

  useEffect(() => {
    form.reset(version)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version])

  const formValues = useWatch({ control: form.control })
  const descriptionWatch = form.watch('description')
  const statusWatch = form.watch('status')
  const versionNumberWatch = form.watch('versionNumber')
  const sourceWatch = form.watch('source')

  const isDraftMode = statusWatch === STATUS_TYPES.DRAFT

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
    if (statusWatch === STATUS_TYPES.AVAILABLE && !canSetAvailable) {
      form.setValue('status', STATUS_TYPES.DRAFT, { shouldDirty: true })
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
    if (versionNumberWatch.length > 0 && descriptionWatch.length > 0 && sourceWatch) {
      completed.push('versionInfo')
    }
    return completed
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [formValues])

  const handleStatusChange = (newStatus: Status) => {
    form.setValue('status', newStatus, { shouldDirty: true })
  }

  const handleCreateUser = () => {
    const values = form.getValues()
    const parsed = isDraftMode
      ? DatastructureVersionFormDraftSchema.safeParse(values)
      : DatastructureVersionFormAvailableSchema.safeParse(values)
    if (!parsed.success) {
      console.error(parsed.error)
      toast.error(tCommon('errors.formInvalid'))
      return
    }

    createVersion.mutate(parsed.data, {
      onSuccess: ({ data }) => {
        toast.success(t('messages.createSuccess'))
        setIsExitModalOpen(false)
        router.push(`/datasructues/${datastructureId}/${data.id}`)
      },
      onError: () => toast.error(tCommon('errors.unexpectedError')),
    })
  }

  const handleUpdateUser = () => {
    const values = form.getValues()
    const parsed = isDraftMode
      ? DatastructureVersionFormDraftSchema.safeParse(values)
      : DatastructureVersionFormAvailableSchema.safeParse(values)
    if (!parsed.success) {
      console.error(parsed.error)
      toast.error(tCommon('errors.formInvalid'))
      return
    }

    updateVersion.mutate(parsed.data, {
      onSuccess: () => {
        toast.success(t('messages.updateSuccess'))
        setIsExitModalOpen(false)
        router.refresh()
      },
      onError: () => toast.error(tCommon('errors.unexpectedError')),
    })
  }

  const handleSave = isCreateMode ? handleCreateUser : handleUpdateUser

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
      !!form.formState.errors.versionNumber ||
      (statusWatch !== STATUS_TYPES.DRAFT && Object.keys(form.formState.errors).length > 0) ||
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
    switch (selectedTab) {
      case 'versionInfo':
        return <VersionInfoTab form={form} isReadOnly={isReadOnly} />
      case 'structure':
        return (
          <StructureDefinitionTab
            versions={datastructure.versions}
            rowCount={datastructure.versions.length}
            isReadOnly={isReadOnly}
          />
        )
      default:
        return null
    }
  }

  return (
    <PageContainer testId={testId} headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader
        title={title}
        segmentedControlBarProps={{
          tabs: tabs,
          selectedTab: selectedTab,
          onTabChange: setSelectedTab,
          completedTabs,
          hasCompletionStatus: true,
        }}
        customElement={isReadOnly ? EditButton : ActionButtonsAndStatusSwitch}
      />
      <PageBackground className="overflow-y-auto" hasBackground={!isReadOnly}>
        <Form {...form}>
          <form
            data-testid="datastructureEditForm"
            aria-label={`${tCommon('form')} ${t('edit.basicInfo.title')}`}
            onSubmit={e => e.preventDefault()}
          >
            {isLoading ? <LoadingSpinner className="h-[300px]" /> : renderTabContent()}
          </form>
        </Form>
      </PageBackground>

      <ExitWarningModal
        isOpen={isExitModalOpen}
        onClose={() => setIsExitModalOpen(false)}
        onDiscard={handleExit}
        onSave={handleSave}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
