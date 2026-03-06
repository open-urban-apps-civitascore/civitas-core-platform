'use client'

import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useState } from 'react'

import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import PageEditControls from '@/components/page-edit-controls/PageEditControls'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Form } from '@/components/ui/form'
import { Datasource, DATASOURCE_STATUS_TYPES, DatasourceTab } from '@/types/datasources'

import { useDatasourceForm } from '../hooks/useDatasourceForm'
import { BasicInfoTab } from './basic-info/BasicInfoTab'
import { ConnectorTab } from './connector-tab/ConnectorTab'

interface DatasourceOverviewProps {
  datasource: Datasource
}

const tabs: Tab<DatasourceTab>[] = [
  { value: 'basicInfo', label: 'datasources.tabs.basicInfo' },
  { value: 'connector', label: 'datasources.tabs.connector' },
  { value: 'dataStructure', label: 'datasources.tabs.dataStructure' },
  { value: 'accessPermissions', label: 'datasources.tabs.accessPermissions' },
]

const disabledTabs: DatasourceTab[] = ['dataStructure', 'accessPermissions']

export const DatasourceOverview = ({ datasource }: DatasourceOverviewProps) => {
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')
  const router = useRouter()
  const searchParams = useSearchParams()
  const pathname = usePathname()
  const [isReadOnly, setIsReadOnly] = useState(searchParams.get('mode') !== 'edit')

  useEffect(() => {
    setIsReadOnly(searchParams.get('mode') !== 'edit')
  }, [searchParams])

  const updateMode = useCallback(
    (isEditing: boolean) => {
      setIsReadOnly(!isEditing)
      const params = new URLSearchParams(searchParams.toString())
      if (isEditing) {
        params.set('mode', 'edit')
      } else {
        params.delete('mode')
      }
      const query = params.toString()
      router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false })
    },
    [pathname, searchParams, router],
  )

  const {
    form,
    readyConnectorType,
    dataSourceStatus,
    handleStatusChange,
    canSetAvailable,
    completedTabs,
    submitDatasource,
    isLoading,
  } = useDatasourceForm(datasource)

  const [selectedTab, setSelectedTab] = useState<DatasourceTab>('basicInfo')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const handleSave = () => {
    submitDatasource(() => router.refresh())
  }

  const handleExit = () => {
    if (form.formState.isDirty) {
      setIsExitModalOpen(true)
    } else {
      form.reset()
      updateMode(false)
    }
  }

  const handleDiscardAndExit = () => {
    setIsExitModalOpen(false)
    form.reset()
    updateMode(false)
  }

  const handleSaveAndExit = () => {
    submitDatasource(() => {
      setIsExitModalOpen(false)
      updateMode(false)
    })
  }

  const renderTabContent = () => {
    switch (selectedTab) {
      case 'basicInfo':
        return <BasicInfoTab form={form} isReadOnly={isReadOnly} />
      case 'connector':
        return <ConnectorTab form={form} readyConnectorType={readyConnectorType} isReadOnly={isReadOnly} />
      case 'dataStructure':
      case 'accessPermissions':
      default:
        return null
    }
  }

  return (
    <PageContainer testId="datasourceOverviewPage" headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader
        title={datasource.name}
        segmentedControlBarProps={{
          tabs: tabs,
          selectedTab: selectedTab,
          onTabChange: setSelectedTab,
          completedTabs,
          disabledTabs,
          hasCompletionStatus: true,
        }}
        customElement={
          <PageEditControls<DatasourceStatusType>
            status={dataSourceStatus}
            onStatusChange={handleStatusChange}
            statusOptions={Object.values(STATUS_TYPES)}
            canSetAvailable={canSetAvailable}
            confirmButtonType="button"
            onConfirmClick={handleSave}
            isConfirmButtonDisabled={
              !form.formState.isDirty ||
              !!form.formState.errors.name ||
              (dataSourceStatus !== STATUS_TYPES.DRAFT && Object.keys(form.formState.errors).length > 0) ||
              isLoading
            }
            isCancelButtonDisabled={isLoading}
            onCancelClick={handleExit}
            hasCard={false}
            isReadOnly={isReadOnly}
            onEditClick={() => updateMode(true)}
            cancelButtonTitle={tCommon('actions.exit')}
            wrapperClassname="w-auto"
          />
        }
      />
      <PageBackground className="overflow-y-auto" hasBackground={!isReadOnly}>
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
