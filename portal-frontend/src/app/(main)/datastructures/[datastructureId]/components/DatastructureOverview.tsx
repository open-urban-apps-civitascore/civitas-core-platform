'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import { useUpdateDatastructure } from '@/app/services/api/datastructures/clientRequests'
import { ExitWarningModal } from '@/components/exit-warning-modal/ExitWarningModal'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { SegmentedControlBar, Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { StatusDropdown } from '@/components/status-dropdown/StatusDropdown'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { Status, STATUS_TYPES } from '@/types/common'
import {
  Datastructure,
  DatastructureFormAvailableSchema,
  DatastructureFormDraft,
  DatastructureFormDraftSchema,
  DatastructureTab,
} from '@/types/datastructures'

import { BasicInfoTab } from './basic-info/BasicInfoTab'

const tabs: Tab<DatastructureTab>[] = [
  {
    value: 'basicInfo',
    label: 'datastructures.tabs.basicInfo',
  },
  {
    value: 'versions',
    label: 'datastructures.tabs.versions',
  },
  {
    value: 'accessPermissions',
    label: 'datastructures.tabs.accessPermissions',
  },
]

const disabledTabs: DatastructureTab[] = ['versions', 'accessPermissions']

interface DatastructureOverviewProps {
  datastructure: Datastructure
}

export const DatastructureOverview = (props: DatastructureOverviewProps) => {
  const { datastructure } = props
  const t = useTranslations('datastructures')
  const tCommon = useTranslations('common')

  const defaultValues: DatastructureFormDraft = {
    id: datastructure.id,
    name: datastructure.name ?? '',
    description: datastructure.description ?? '',
    status: datastructure.status ?? STATUS_TYPES.DRAFT,
  }

  const router = useRouter()
  const searchParams = useSearchParams()

  const [selectedTab, setSelectedTab] = useState<DatastructureTab>('basicInfo')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  const updateDatastructure = useUpdateDatastructure()
  const isLoading = updateDatastructure.isPending

  const form = useForm<DatastructureFormDraft>({
    resolver: zodResolver(DatastructureFormDraftSchema),
    mode: 'onChange',
    defaultValues,
  })

  useEffect(() => {
    form.reset(defaultValues)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [datastructure])

  const formValues = useWatch({ control: form.control })
  const statusWatch = form.watch('status')
  const nameWatch = form.watch('name')
  const descriptionWatch = form.watch('description')

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

  const completedTabs = useMemo((): DatastructureTab[] => {
    const completed: DatastructureTab[] = []
    if (nameWatch.length > 0 && descriptionWatch.length > 0) {
      completed.push('basicInfo')
    }
    return completed
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [formValues])

  const handleStatusChange = (newStatus: Status) => {
    form.setValue('status', newStatus, { shouldDirty: true })
  }

  const submitDatastructure = (onSuccess?: () => void) => {
    const values = form.getValues()
    const parsed = isDraftMode
      ? DatastructureFormDraftSchema.safeParse(values)
      : DatastructureFormAvailableSchema.safeParse(values)
    if (!parsed.success) {
      console.error(parsed.error)
      toast.error('Form data invalid')
      return
    }

    // eslint-disable-next-line unused-imports/no-unused-vars
    const { status, ...updateData } = parsed.data

    updateDatastructure.mutate({ ...updateData, id: values.id }, { onSuccess: () => onSuccess?.() })
  }

  const handleSave = () => {
    submitDatastructure(() => router.refresh())
  }

  const handleExit = () => {
    if (form.formState.isDirty) {
      setIsExitModalOpen(true)
    } else {
      router.push(`/datastructures?${searchParams.toString()}`)
    }
  }

  const handleDiscardAndExit = () => {
    setIsExitModalOpen(false)
    router.push(`/datastructures?${searchParams.toString()}`)
  }

  const handleSaveAndExit = () => {
    submitDatastructure(() => {
      setIsExitModalOpen(false)
      router.push(`/datastructures?${searchParams.toString()}`)
    })
  }

  const renderTabContent = () => {
    switch (selectedTab) {
      case 'basicInfo':
        return <BasicInfoTab form={form} />
      default:
        return null
    }
  }

  return (
    <PageContainer testId="datastructureOverviewPage" headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <div className="w-full flex flex-col h-[var(--title-height)] py-[var(--layout-padding)] border-b-1">
        <div className="flex items-center justify-between px-[var(--layout-padding)]">
          <h1 className="text-3xl font-bold truncate max-w-full min-w-0">{datastructure.name}</h1>
          <div className="flex items-center gap-4">
            <StatusDropdown
              status={statusWatch}
              onStatusChange={handleStatusChange}
              canSetAvailable={canSetAvailable}
            />
            <Button
              data-testid="exitButton"
              type="button"
              variant="secondary"
              onClick={handleExit}
              disabled={isLoading}
            >
              {tCommon('actions.exit')}
            </Button>
            <Button
              data-testid="saveButton"
              type="button"
              onClick={handleSave}
              disabled={
                !form.formState.isDirty ||
                !!form.formState.errors.name ||
                (statusWatch !== STATUS_TYPES.DRAFT && Object.keys(form.formState.errors).length > 0) ||
                isLoading
              }
            >
              {tCommon('actions.submit')}
            </Button>
          </div>
        </div>
        <div className="mt-4 px-[var(--layout-padding)]">
          <SegmentedControlBar
            tabs={tabs}
            selectedTab={selectedTab}
            onTabChange={setSelectedTab}
            completedTabs={completedTabs}
            disabledTabs={disabledTabs}
          />
        </div>
      </div>
      <PageBackground className="overflow-y-auto">
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
        onDiscard={handleDiscardAndExit}
        onSave={handleSaveAndExit}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
