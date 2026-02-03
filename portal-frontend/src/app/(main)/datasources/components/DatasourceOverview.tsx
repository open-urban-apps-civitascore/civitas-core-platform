'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'

import { useUpdateDatasource } from '@/app/services/api/datasources/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { CONNECTOR_TYPES, DATASOURCE_STATUS_TYPES } from '@/const/datasources'
import { cn } from '@/lib/utils'
import { ConnectorType, getConnectorDefaults } from '@/types/connectors'
import {
  ConnectorFieldOptions,
  Datasource,
  DatasourceFormInput,
  DatasourceFormSchema,
  DatasourceStatusType,
} from '@/types/datasources'

import { BasicInfoTab } from './basic-info/BasicInfoTab'
import { NODE_DEFS } from './connector-tab/connector_sources'
import { ConnectorTab } from './connector-tab/ConnectorTab'
import { ExitWarningModal } from './ExitWarningModal'
import { DatasourceTab, SegmentedControlBar } from './SegmentedControlBar'
import { StatusDropdown } from './StatusDropdown'

interface DatasourceOverviewProps {
  datasource: Datasource
}

export const DatasourceOverview = (props: DatasourceOverviewProps) => {
  const { datasource } = props
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')

  const getConnector = (type: ConnectorType | undefined) =>
    type === 'sql'
      ? ({
          type: 'sql',
          config: getConnectorDefaults(CONNECTOR_TYPES.SQL),
        } as const)
      : ({
          type: 'mqtt',
          config: getConnectorDefaults(CONNECTOR_TYPES.MQTT),
        } as const)

  const defaultValues: DatasourceFormInput = {
    id: datasource.id,
    name: datasource.name ?? '',
    description: datasource.description ?? '',
    tags: datasource.tags ?? [],
    status: datasource.status ?? DATASOURCE_STATUS_TYPES.DRAFT,
    connector: getConnector(datasource.connector?.type),
  }

  const router = useRouter()
  const searchParams = useSearchParams()

  const [selectedTab, setSelectedTab] = useState<DatasourceTab>('basicInfo')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  const updateDatasource = useUpdateDatasource()
  const isLoading = updateDatasource.isPending

  const form = useForm<DatasourceFormInput>({
    resolver: zodResolver(DatasourceFormSchema),
    mode: 'onChange',
    defaultValues,
  })
  const connectorTypeWatch = form.watch('connector.type') as ConnectorType
  const connectorConfig = useMemo(
    () => (connectorTypeWatch ? (NODE_DEFS[connectorTypeWatch].properties as ConnectorFieldOptions[]) : []),
    [connectorTypeWatch],
  )

  useEffect(() => {
    if (!connectorTypeWatch) return

    form.setValue('connector', getConnector(connectorTypeWatch), { shouldDirty: true })
  }, [connectorTypeWatch, form])

  const statusWatch = form.watch('status')
  const nameWatch = form.watch('name')
  const descriptionWatch = form.watch('description')

  useEffect(() => {
    if (statusWatch === DATASOURCE_STATUS_TYPES.AVAILABLE) {
      void form.trigger()
    }
  }, [statusWatch, connectorTypeWatch, form])

  const isDraftMode = statusWatch === DATASOURCE_STATUS_TYPES.DRAFT

  useEffect(() => {
    if (isDraftMode) {
      form.clearErrors()
    } else {
      form.trigger()
    }
  }, [isDraftMode])

  const formValues = useWatch({ control: form.control })

  // Allow "Available" only when the form would be valid in AVAILABLE mode
  const canSetAvailable = useMemo(() => {
    const values = form.getValues()
    return DatasourceFormSchema.safeParse(values).success
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [formValues])

  // Auto-revert status to draft when required fields become empty
  useEffect(() => {
    if (statusWatch === DATASOURCE_STATUS_TYPES.AVAILABLE && !canSetAvailable) {
      form.setValue('status', DATASOURCE_STATUS_TYPES.DRAFT, { shouldDirty: true })
    }
  }, [canSetAvailable, statusWatch, form])

  // Check which tabs are completed
  const completedTabs = useMemo((): DatasourceTab[] => {
    const completed: DatasourceTab[] = []
    if (nameWatch.length > 0 && descriptionWatch.length > 0) {
      completed.push('basicInfo')
    }
    // Other tabs would have their completion logic here
    return completed
  }, [nameWatch, descriptionWatch])

  // Tabs that are disabled (for future implementation)
  const disabledTabs: DatasourceTab[] = ['dataStructure', 'accessPermissions', 'dataspaces']

  const handleStatusChange = (newStatus: DatasourceStatusType) => {
    form.setValue('status', newStatus, { shouldDirty: true })
  }

  // const getTypedConfig = <T extends ConnectorType>(type: T, config: unknown): ConnectorConfigSchemaType[T] => {
  //   return config as ConnectorConfigSchemaType[T]
  // }

  const buildApiPayload = (formData: DatasourceFormInput): Datasource => {
    return {
      id: formData.id,
      name: formData.name,
      description: formData.description,
      tags: formData.tags,
      status: formData.status,
      lastActive: datasource.lastActive,
      connection: datasource.connection,
      connector: {
        type: formData.connector?.type,
        config: formData.connector?.config || null,
      },
    }
  }

  const submitDatasource = (data: DatasourceFormInput) => {
    if (isDraftMode) {
      // no validation block
      updateDatasource.mutate(buildApiPayload(data))
      return
    }

    form.handleSubmit(values => {
      const parsed = DatasourceFormSchema.parse(values)
      updateDatasource.mutate(buildApiPayload(parsed))
    })()
  }

  // const submitDatasource = (onSuccess: (data: DatasourceFormData) => void) => {
  //   const submitHandler = (formInput: DatasourceFormInput) => {
  //     const parsed = DatasourceFormSchema.parse(formInput)

  //     updateDatasource.mutate(parsed, {
  //       onSuccess: () => onSuccess(parsed),
  //     })
  //   }

  //   if (isDraftMode) {
  //     if (nameWatch.length > 0) {
  //       submitHandler(form.getValues())
  //     }
  //   } else {
  //     form.handleSubmit(submitHandler)()
  //   }
  // }

  const handleSave = () => {
    submitDatasource(form.getValues())
    form.reset(form.getValues())
    router.refresh()
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
    submitDatasource(form.getValues())
    setIsExitModalOpen(false)
    router.push(`/datasources?${searchParams.toString()}`)
  }

  const renderTabContent = () => {
    switch (selectedTab) {
      case 'basicInfo':
        return <BasicInfoTab form={form} isDraftMode={isDraftMode} />
      case 'connector':
        return (
          <ConnectorTab
            form={form}
            isDraftMode={isDraftMode}
            config={connectorConfig}
            connectorType={connectorTypeWatch}
          />
        )
      case 'dataStructure':
      case 'accessPermissions':
      case 'dataspaces':
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
              {t('actions.exit')}
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
        <ContentCard className={cn('h-full overflow-auto')}>
          <Form {...form}>
            <form
              data-testid="datasourceEditForm"
              aria-label={`${tCommon('form')} ${t('edit.basicInfo.title')}`}
              onSubmit={e => e.preventDefault()}
            >
              {isLoading ? <LoadingSpinner className="h-[300px]" /> : renderTabContent()}
            </form>
          </Form>
        </ContentCard>
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
