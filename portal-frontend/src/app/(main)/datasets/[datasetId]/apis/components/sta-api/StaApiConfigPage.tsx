'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo } from 'react'
import { useForm } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { Form } from '@/components/ui/form'
import { Dataset } from '@/types/datasets'
import { API_TYPE_QUERY, DEFAULTS_BY_TYPE, NamedApi, StaApiFormData, StaApiFormSchema } from '@/types/namedApis'

import { useApiConfig } from '../../hooks/useApiConfig'
import { ApiConfigTab, ApiConfigWrapper } from '../ApiConfigWrapper'
import { BaseInfoForm } from '../base-info/BaseInfoForm'

interface StaApiConfigPageProps {
  dataset: Dataset
  existingApi?: NamedApi
  testId?: string
}

export const StaApiConfigPage = ({ dataset, existingApi, testId }: StaApiConfigPageProps) => {
  const t = useTranslations('datasets.overview.completion.apis.config')
  const apiType = API_TYPE_QUERY.SENSORTHINGS
  const defaults = DEFAULTS_BY_TYPE[apiType]

  const otherNamedApis = useMemo(
    () => (dataset.namedApis ?? []).filter(a => a.slug !== existingApi?.slug),
    [dataset.namedApis, existingApi?.slug],
  )
  const existingSlugs = useMemo(() => otherNamedApis.map(a => a.slug), [otherNamedApis])
  const formSchema = useMemo(() => StaApiFormSchema({ existingSlugs }), [existingSlugs])
  const initialSlug = existingApi?.slug ?? defaults.defaultSlug

  const staDefaults: StaApiFormData = {
    type: API_TYPE_QUERY.SENSORTHINGS,
    baseInfo: {
      name: existingApi?.name ?? '',
      slug: initialSlug,
      description: existingApi?.description ?? '',
      persistence: defaults.persistenceValue,
    },
  }

  const form = useForm<StaApiFormData>({
    resolver: zodResolver(formSchema),
    mode: 'onChange',
    defaultValues: staDefaults,
  })

  useEffect(() => {
    form.reset(staDefaults)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [existingApi?.id])

  const {
    isReadOnly,
    isLoading,
    isExitModalOpen,
    setIsExitModalOpen,
    urlPreviewSlug,
    updateMode,
    handleSlugBlur,
    handleExit,
    handleDiscardAndExit,
    handleSaveAndExit,
    handleSubmit,
  } = useApiConfig({
    form,
    dataset,
    existingApi,
    otherNamedApis,
    initialSlug,
  })

  const typeLabel = t(`title.${apiType}`)
  const tabs = [
    { value: 'basicInfo' as ApiConfigTab, label: 'datasets.overview.completion.apis.config.tabs.basicInfo' },
  ]
  const completedTabs: ApiConfigTab[] = form.formState.isValid ? ['basicInfo'] : []

  return (
    <ApiConfigWrapper
      dataset={dataset}
      isReadOnly={isReadOnly}
      hasUnsavedChanges={form.formState.isDirty}
      isFormValid={form.formState.isValid}
      isLoading={isLoading}
      tabs={tabs}
      selectedTab="basicInfo"
      onTabChange={() => {}}
      completedTabs={completedTabs}
      typeLabel={typeLabel}
      testId={testId}
      isExitModalOpen={isExitModalOpen}
      onExitModalOpenChange={setIsExitModalOpen}
      onEdit={() => updateMode(true)}
      onExit={handleExit}
      onDiscard={handleDiscardAndExit}
      onSaveAndExit={handleSaveAndExit}
      onSubmit={handleSubmit}
    >
      <Form {...form}>
        <ContentCard>
          <BaseInfoForm
            form={form}
            apiType={apiType}
            isReadOnly={isReadOnly}
            datasetId={dataset.id}
            typeLabel={typeLabel}
            urlPreviewSlug={urlPreviewSlug}
            onSlugBlur={handleSlugBlur}
          />
        </ContentCard>
      </Form>
    </ApiConfigWrapper>
  )
}
