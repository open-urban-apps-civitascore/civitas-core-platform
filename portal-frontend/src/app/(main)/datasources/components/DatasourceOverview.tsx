'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Resolver, useForm, useWatch } from 'react-hook-form'

import { useUpdateDatasource } from '@/app/services/api/datasources/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { cn } from '@/lib/utils'
import {
  BaseDatasource,
  buildConnectorDefaultConfig,
  buildDatasourceFormSchema,
  ConnectorConfig,
  ConnectorType,
  DATASOURCE_STATUS_TYPES,
  DatasourceBaseFormData,
  DatasourceFormData,
  DatasourceStatusType,
} from '@/types/datasources'

import { BasicInfoTab } from './basic-info/BasicInfoTab'
import { NODE_DEFS } from './connector-tab/connector_sources'
import { ExitWarningModal } from './ExitWarningModal'
import { DatasourceTab, SegmentedControlBar } from './SegmentedControlBar'
import { StatusDropdown } from './StatusDropdown'
import { ConnectorTab } from './connector-tab/ConnectorTab'

interface DatasourceOverviewProps {
  datasource: BaseDatasource
}

export const DatasourceOverview = (props: DatasourceOverviewProps) => {
  const { datasource } = props
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')

  const router = useRouter()
  const searchParams = useSearchParams()

  const [selectedTab, setSelectedTab] = useState<DatasourceTab>('basicInfo')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  const updateDatasource = useUpdateDatasource()
  const isLoading = updateDatasource.isPending

  const resolver: Resolver<DatasourceFormData> = useCallback(async values => {
    if ((values.status ?? DATASOURCE_STATUS_TYPES.DRAFT) === DATASOURCE_STATUS_TYPES.DRAFT) {
      return { values, errors: {} }
    }
    const connectorConfig = values.connector?.type
      ? (NODE_DEFS[values.connector.type as ConnectorType].properties as ConnectorConfig[])
      : []
    const schema = buildDatasourceFormSchema(connectorConfig, values.status ?? DATASOURCE_STATUS_TYPES.DRAFT)
    return zodResolver(schema)(values, {}, {})
  }, [])

  const form = useForm<DatasourceFormData>({
    resolver,
    mode: 'onChange',
    reValidateMode: 'onChange',
    defaultValues: {
      id: datasource.id,
      name: datasource.name,
      description: datasource.description,
      tags: datasource.tags,
      status: datasource.status,
      connector: {
        type: datasource.connector.type,
        config: null,
      },
    },
  })

  const connectorTypeWatch = form.watch('connector.type')
  const connectorConfig = useMemo(
    () => (connectorTypeWatch ? (NODE_DEFS[connectorTypeWatch].properties as ConnectorConfig[]) : []),
    [connectorTypeWatch],
  )

  useEffect(() => {
    const defaults = buildConnectorDefaultConfig(connectorConfig)
    form.reset(form.getValues(), {
      keepValues: true,
      keepDirty: true,
    })
    form.setValue('connector.config', defaults)
  }, [connectorConfig, form])

  const statusWatch = form.watch('status')
  const nameWatch = form.watch('name')
  const descriptionWatch = form.watch('description')

  useEffect(() => {
    void form.trigger()
  }, [statusWatch, connectorTypeWatch, form])

  const isDraftMode = statusWatch === DATASOURCE_STATUS_TYPES.DRAFT

  const formValues = useWatch({ control: form.control })

  // Allow "Available" only when the form would be valid in AVAILABLE mode
  const canSetAvailable = useMemo(() => {
    const schema = buildDatasourceFormSchema(connectorConfig, DATASOURCE_STATUS_TYPES.AVAILABLE)
    return schema.safeParse(formValues ?? form.getValues()).success
  }, [formValues, connectorConfig, form])

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

  const submitDatasource = (onSuccess: (data: DatasourceFormData) => void) => {
    const submitHandler = (formData: DatasourceFormData) => {
      const data = {
        id: formData.id,
        name: formData.name,
        description: formData.description,
        tags: formData.tags,
        status: formData.status,
        connector: {
          type: formData.connector.type,
          config: formData.connector.config,
        },
      }
      updateDatasource.mutate(data, { onSuccess: () => onSuccess(data) })
    }

    if (isDraftMode) {
      // In draft mode, only validate name (always required) and bypass other validation
      if (nameWatch.length > 0) {
        submitHandler(form.getValues())
      }
    } else {
      form.handleSubmit(submitHandler)()
    }
  }

  const handleSave = () => {
    submitDatasource(data => {
      form.reset(data)
      router.refresh()
    })
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
        return <BasicInfoTab form={form} isDraftMode={isDraftMode} />
      case 'connector':
        return <ConnectorTab form={form} isDraftMode={isDraftMode} config={connectorConfig} />
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
