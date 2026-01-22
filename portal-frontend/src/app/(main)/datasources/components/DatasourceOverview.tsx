'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'

import { useUpdateDatasource } from '@/app/services/api/datasources/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { cn } from '@/lib/utils'
import { Datasource, DatasourceFormData, DatasourceFormSchema, DatasourceStatusType } from '@/types/datasources'

import { BasicInfoTab } from './basic-info/BasicInfoTab'
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

  const router = useRouter()
  const searchParams = useSearchParams()

  const [selectedTab, setSelectedTab] = useState<DatasourceTab>('basicInfo')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  const updateDatasource = useUpdateDatasource()
  const isLoading = updateDatasource.isPending

  const form = useForm<DatasourceFormData>({
    resolver: zodResolver(DatasourceFormSchema),
    mode: 'onChange',
    defaultValues: {
      id: datasource.id,
      name: datasource.name,
      description: datasource.description,
      tags: datasource.tags,
      status: datasource.status,
    },
  })

  const statusWatch = form.watch('status')
  const nameWatch = form.watch('name')
  const descriptionWatch = form.watch('description')

  // Check if all mandatory fields are filled to enable "Available" status
  const canSetAvailable = useMemo(() => {
    return nameWatch.length > 0 && descriptionWatch.length > 0
  }, [nameWatch, descriptionWatch])

  // Auto-revert status to draft when required fields become empty
  useEffect(() => {
    if (statusWatch === 'available' && !canSetAvailable) {
      form.setValue('status', 'draft', { shouldDirty: true })
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
  const disabledTabs: DatasourceTab[] = ['connector', 'dataStructure', 'accessPermissions', 'dataspaces']

  const handleStatusChange = (newStatus: DatasourceStatusType) => {
    form.setValue('status', newStatus, { shouldDirty: true })
  }

  const handleSaveSubmit = (formData: DatasourceFormData) => {
    const data = {
      id: formData.id,
      name: formData.name,
      description: formData.description,
      tags: formData.tags,
      status: formData.status,
    }
    updateDatasource.mutate(data, {
      onSuccess: () => {
        form.reset(data)
        router.refresh()
      },
    })
  }

  const handleSave = form.handleSubmit(handleSaveSubmit)

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

  const handleSaveAndExitSubmit = (formData: DatasourceFormData) => {
    const data = {
      id: formData.id,
      name: formData.name,
      description: formData.description,
      tags: formData.tags,
      status: formData.status,
    }
    updateDatasource.mutate(data, {
      onSuccess: () => {
        setIsExitModalOpen(false)
        router.push(`/datasources?${searchParams.toString()}`)
      },
    })
  }

  const handleSaveAndExit = form.handleSubmit(handleSaveAndExitSubmit)

  const renderTabContent = () => {
    switch (selectedTab) {
      case 'basicInfo':
        return <BasicInfoTab form={form} />
      case 'connector':
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
              disabled={!form.formState.isDirty || Object.keys(form.formState.errors).length > 0 || isLoading}
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
