'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import { useUpdateDatasource } from '@/app/services/api/datasources/clientRequests'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { DATASOURCE_STATUS_TYPES } from '@/const/connectors'
import { ConnectorApiToFormSchema, ConnectorStrictSchema, ConnectorType } from '@/types/connectors'
import {
  Datasource,
  DatasourceFormAvailableSchema,
  DatasourceFormDraft,
  DatasourceFormDraftSchema,
  DatasourceFormToApiSchema,
  DatasourceStatusType,
} from '@/types/datasources'
import { getConnectorFormData } from '@/utils/connectors'
import { pickDirtyValues } from '@/utils/form'

import { BasicInfoTab } from './basic-info/BasicInfoTab'
import { ConnectorTab } from './connector-tab/ConnectorTab'
import { DatasourceTab, SegmentedControlBar } from './SegmentedControlBar'
import { StatusDropdown } from './StatusDropdown'

interface DatasourceOverviewProps {
  datasource: Datasource
}

export const DatasourceOverview = (props: DatasourceOverviewProps) => {
  const { datasource } = props
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')

  const defaultValues: DatasourceFormDraft = {
    id: datasource.id,
    name: datasource.name ?? '',
    description: datasource.description ?? '',
    status: datasource.status ?? DATASOURCE_STATUS_TYPES.DRAFT,
    connector: ConnectorApiToFormSchema.safeParse(datasource.connector).data ?? null,
  }

  const router = useRouter()
  const searchParams = useSearchParams()

  const [selectedTab, setSelectedTab] = useState<DatasourceTab>('basicInfo')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

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
  const statusWatch = form.watch('status')
  const nameWatch = form.watch('name')
  const descriptionWatch = form.watch('description')
  const connectorTypeWatch = form.watch('connector.type')

  const isDraftMode = statusWatch === DATASOURCE_STATUS_TYPES.DRAFT

  // Allow "Available" only when the form would be valid in AVAILABLE mode
  const canSetAvailable = useMemo(() => {
    return DatasourceFormAvailableSchema.safeParse(formValues).success
  }, [formValues])

  const updateConnectorConfig = (connectorType: ConnectorType) =>
    form.setValue('connector', getConnectorFormData(connectorType, defaultValues.connector), { shouldDirty: true })

  const revalidateForm = () => {
    if (!isDraftMode) {
      void form.trigger()
    }
  }
  // Auto-revert status to draft when required fields become empty
  const revalidateDraftMode = () => {
    if (statusWatch === DATASOURCE_STATUS_TYPES.AVAILABLE && !canSetAvailable) {
      form.setValue('status', DATASOURCE_STATUS_TYPES.DRAFT, { shouldDirty: true })
      toast.info(tCommon('info.switchMode'))
    }
  }

  useEffect(() => {
    if (!connectorTypeWatch) return
    updateConnectorConfig(connectorTypeWatch)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectorTypeWatch])

  useEffect(() => {
    if (isDraftMode) {
      form.clearErrors()
    } else {
      revalidateForm()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectorTypeWatch, isDraftMode])

  useEffect(() => {
    revalidateDraftMode()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canSetAvailable, statusWatch])

  const completedTabs = useMemo((): DatasourceTab[] => {
    const completed: DatasourceTab[] = []
    if (nameWatch.length > 0 && descriptionWatch.length > 0) {
      completed.push('basicInfo')
    }
    if (ConnectorStrictSchema.safeParse(formValues.connector).success) completed.push('connector')
    return completed
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [formValues])

  // Tabs that are disabled (for future implementation)
  const disabledTabs: DatasourceTab[] = ['dataStructure', 'accessPermissions']

  const handleStatusChange = (newStatus: DatasourceStatusType) => {
    form.setValue('status', newStatus, { shouldDirty: true })
  }

  const submitDatasource = (onSuccess?: () => void) => {
    const values = form.getValues()
    const parsed = isDraftMode
      ? DatasourceFormToApiSchema.safeParse(values)
      : DatasourceFormAvailableSchema.safeParse(values)
    if (!parsed.success) {
      console.error(parsed.error)
      toast.error('Form data invalid')
      return
    }

    const dirtyFields = form.formState.dirtyFields
    const updateData = pickDirtyValues(parsed.data, dirtyFields)

    // use this implementation once json-server is not used anymore.
    // json-server can not patch nested values, so patching the connector config does not work with json-server
    // if (updateData.connector && !defaultValues.connector) {
    // updateData.connector = parsed.data.connector
    // }
    updateData.connector = parsed.data.connector

    updateDatasource.mutate({ ...updateData, id: values.id }, { onSuccess: () => onSuccess?.() })
  }

  const handleSave = () => {
    submitDatasource(() => router.refresh())
  }

  const handleExit = () => {
    if (form.formState.isDirty) {
      setIsExitModalOpen(true)
    } else {
      router.push(`/datasources?${searchParams.toString()}`)
    }
  }

  const handleDiscardAndExit = () => {
    setIsExitModalOpen(false)
    router.push(`/datasources?${searchParams.toString()}`)
  }

  const handleSaveAndExit = () => {
    submitDatasource(() => {
      setIsExitModalOpen(false)
      router.push(`/datasources?${searchParams.toString()}`)
    })
  }

  const renderTabContent = () => {
    switch (selectedTab) {
      case 'basicInfo':
        return <BasicInfoTab form={form} />
      case 'connector':
        return <ConnectorTab form={form} isDraftMode={isDraftMode} onConnectorTypeChange={updateConnectorConfig} />
      case 'dataStructure':
      case 'accessPermissions':
      default:
        return null
    }
  }

  return (
    <PageContainer testId="datasourceOverviewPage" headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <div className="w-full flex flex-col h-[var(--title-height)] py-[var(--layout-padding)] border-b-1">
        <div className="flex items-center justify-between px-[var(--layout-padding)]">
          <h1 className="text-3xl font-bold truncate max-w-full min-w-0">{datasource.name}</h1>
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
                (statusWatch !== DATASOURCE_STATUS_TYPES.DRAFT && Object.keys(form.formState.errors).length > 0) ||
                isLoading
              }
            >
              {tCommon('actions.submit')}
            </Button>
          </div>
        </div>
        <div className="mt-4 px-[var(--layout-padding)]">
          <SegmentedControlBar
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
            data-testid="datasourceEditForm"
            aria-label={`${tCommon('form')} ${t('edit.basicInfo.title')}`}
            onSubmit={e => e.preventDefault()}
          >
            {isLoading ? <LoadingSpinner className="h-[300px]" /> : renderTabContent()}
          </form>
        </Form>
      </PageBackground>

      <ExitWarningModal
        open={isExitModalOpen}
        isLoading={isLoading}
        onOpenChange={setIsExitModalOpen}
        onDiscard={handleDiscardAndExit}
        onConfirm={handleSaveAndExit}
      />
    </PageContainer>
  )
}
