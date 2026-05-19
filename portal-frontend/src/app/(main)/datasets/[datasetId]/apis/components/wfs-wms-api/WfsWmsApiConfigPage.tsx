'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm, UseFormReturn } from 'react-hook-form'

import { Form } from '@/components/ui/form'
import { Dataset } from '@/types/datasets'
import {
  API_TYPE_QUERY,
  DEFAULTS_BY_TYPE,
  NamedApi,
  NamedApiFormData,
  WfsWmsApiFormData,
  WfsWmsApiFormSchema,
} from '@/types/namedApis'

import { useApiConfig } from '../../hooks/useApiConfig'
import { ApiConfigTab, ApiConfigWrapper } from '../ApiConfigWrapper'
import { BaseInfoForm } from '../base-info/BaseInfoForm'
import { LayerConfig } from './LayerConfig'

interface WfsWmsApiConfigPageProps {
  dataset: Dataset
  existingApi?: NamedApi
  testId?: string
}

export const WfsWmsApiConfigPage = ({ dataset, existingApi, testId }: WfsWmsApiConfigPageProps) => {
  const t = useTranslations('datasets.overview.completion.apis.config')
  const apiType = API_TYPE_QUERY.WFS_WMS
  const defaults = DEFAULTS_BY_TYPE[apiType]

  const otherNamedApis = useMemo(
    () => (dataset.namedApis ?? []).filter(a => a.slug !== existingApi?.slug),
    [dataset.namedApis, existingApi?.slug],
  )
  const existingSlugs = useMemo(() => otherNamedApis.map(a => a.slug), [otherNamedApis])
  const formSchema = useMemo(() => WfsWmsApiFormSchema({ existingSlugs }), [existingSlugs])
  const initialSlug = existingApi?.slug ?? defaults.defaultSlug

  const wfsWmsDefaults = {
    name: existingApi?.name ?? '',
    slug: initialSlug,
    description: existingApi?.description ?? '',
    persistence: defaults.persistenceValue,
    layerName: '',
    technicalLayerName: '',
    layerDescription: '' as string | undefined,
    table: '',
    attributes: [] as string[],
    filter: '',
    geometryField: '',
    crs: '',
    bbox: '',
    style: '',
  }

  const form = useForm<WfsWmsApiFormData>({
    resolver: zodResolver(formSchema),
    mode: 'onBlur',
    defaultValues: wfsWmsDefaults,
  })

  useEffect(() => {
    form.reset(wfsWmsDefaults)
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
    buildPayload: data => ({
      name: data.name.trim(),
      slug: data.slug,
      standard: defaults.standard,
      description: data.description?.trim() || undefined,
    }),
  })

  const [selectedTab, setSelectedTab] = useState<ApiConfigTab>('basicInfo')
  const typeLabel = t(`title.${apiType}`)
  const tabs = [
    { value: 'basicInfo' as ApiConfigTab, label: 'datasets.overview.completion.apis.config.tabs.basicInfo' },
    { value: 'layer' as ApiConfigTab, label: 'datasets.overview.completion.apis.config.tabs.layer' },
    { value: 'styles' as ApiConfigTab, label: 'datasets.overview.completion.apis.config.tabs.styles' },
  ]
  const completedTabs: ApiConfigTab[] = form.formState.isValid ? ['basicInfo'] : []

  return (
    <ApiConfigWrapper
      isReadOnly={isReadOnly}
      hasUnsavedChanges={form.formState.isDirty}
      isFormValid={form.formState.isValid}
      isLoading={isLoading}
      tabs={tabs}
      selectedTab={selectedTab}
      onTabChange={setSelectedTab}
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
        {selectedTab === 'basicInfo' && (
          <BaseInfoForm
            form={form as unknown as UseFormReturn<NamedApiFormData>}
            apiType={apiType}
            isReadOnly={isReadOnly}
            datasetId={dataset.id}
            typeLabel={typeLabel}
            urlPreviewSlug={urlPreviewSlug}
            onSlugBlur={handleSlugBlur}
          />
        )}
        {selectedTab === 'layer' && <LayerConfig form={form} />}
        {selectedTab === 'styles' && (
          <div data-testid={`tabPlaceholder-${selectedTab}`} className="py-12 text-center text-muted-foreground">
            {t('tabs.placeholder')}
          </div>
        )}
      </Form>
    </ApiConfigWrapper>
  )
}
