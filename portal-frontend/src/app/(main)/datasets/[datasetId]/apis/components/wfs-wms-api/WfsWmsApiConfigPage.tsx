'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQueries } from '@tanstack/react-query'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { useForm, UseFormReturn } from 'react-hook-form'

import { useGetDatasinks } from '@/app/services/api/datasinks/clientRequests'
import { useGetLayers } from '@/app/services/api/layers/clientRequests'
import { apiRequest } from '@/app/services/api/request/apiRequest'
import { Form } from '@/components/ui/form'
import { Dataset } from '@/types/datasets'
import { DATASINK_TYPES } from '@/types/datasinks'
import { DatastructureVersion } from '@/types/datastructures'
import {
  API_TYPE_QUERY,
  DEFAULTS_BY_TYPE,
  LayerFormData,
  NamedApi,
  StaApiFormData,
  WfsWmsApiFormData,
  WfsWmsApiFormSchema,
} from '@/types/namedApis'

import { useApiConfig } from '../../hooks/useApiConfig'
import { ApiConfigTab, ApiConfigWrapper } from '../ApiConfigWrapper'
import { BaseInfoForm } from '../base-info/BaseInfoForm'
import { LayerConfig } from './LayerConfig'
import { mockLayerList } from './mockData'

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

  const { data: datasinksData } = useGetDatasinks()

  const { data } = useGetLayers(dataset.id)
  const [isCreateLayerMode, setIsCreateLayerMode] = useState(!!data?.data.length && data?.data.length > 0)

  const layers = mockLayerList

  const postgisDatasinks =
    datasinksData?.data.filter(datasink => datasink.dataSinkType === DATASINK_TYPES.POSTGIS) || []
  const datastructuresToFetch = postgisDatasinks?.map(datasink => ({
    datastructureId: datasink.configuration.dataStructureVersion.dataStructureId,
    versionId: datasink.configuration.dataStructureVersion.id,
  }))

  const postgisDatastructuresResponse = useQueries({
    queries: datastructuresToFetch.map(({ datastructureId, versionId }) => ({
      queryKey: [`datastructures/${datastructureId}/versions`, versionId],
      queryFn: () =>
        apiRequest<DatastructureVersion>({
          endpoint: `/datastructures/${datastructureId}/versions/${versionId}`,
          method: 'GET',
          headers: { 'x-api-request': 'true' },
          errorMessage: 'An error occurred while fetching datastructure versions.',
        }),
    })),
  })

  const validPostgisDatastructureResponses = postgisDatastructuresResponse.filter(res => !!res.data)
  const postgisDatastructures = validPostgisDatastructureResponses.map(datastructure => datastructure.data.data)

  const defaultLayer: LayerFormData = {
    title: '',
    layerName: '',
    layerDescription: '',
    table: '',
    attribute: [],
    cqlFilter: '',
    geometryColumnRef: '',
    crs: '',
    bboxAutoCalculate: false as const,
    nativeBoundingBox: { minX: '', minY: '', maxX: '', maxY: '', crs: '' },
    latLonBoundingBox: { minX: '', minY: '', maxX: '', maxY: '', crs: '' },
    defaultStyleId: '',
    alternativeStyleIds: [],
  }
  const wfsWmsDefaults: WfsWmsApiFormData = {
    type: API_TYPE_QUERY.WFS_WMS,
    baseInfo: {
      name: existingApi?.name ?? '',
      slug: initialSlug,
      description: existingApi?.description ?? '',
      persistence: defaults.persistenceValue,
    },
    layer: defaultLayer,
  }

  const form = useForm<WfsWmsApiFormData>({
    resolver: zodResolver(formSchema),
    mode: 'onChange',
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
    isCreateLayerMode,
  })

  const [selectedTab, setSelectedTab] = useState<ApiConfigTab>('basicInfo')
  const typeLabel = t(`title.${apiType}`)
  const tabs = [
    { value: 'basicInfo' as ApiConfigTab, label: 'datasets.overview.completion.apis.config.tabs.basicInfo' },
    { value: 'layer' as ApiConfigTab, label: 'datasets.overview.completion.apis.config.tabs.layer' },
    { value: 'styles' as ApiConfigTab, label: 'datasets.overview.completion.apis.config.tabs.styles' },
  ]
  const completedTabs: ApiConfigTab[] = form.formState.isValid ? ['basicInfo'] : []

  const handleSelectLayer = (layerId: string) => {
    // TODO: set form value layer to selected layer
    setIsCreateLayerMode(false)
  }

  const handleAddLayer = () => {
    form.setValue('layer', defaultLayer)
    setIsCreateLayerMode(true)
  }

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
            form={form as unknown as UseFormReturn<StaApiFormData>}
            apiType={apiType}
            isReadOnly={isReadOnly}
            datasetId={dataset.id}
            typeLabel={typeLabel}
            urlPreviewSlug={urlPreviewSlug}
            onSlugBlur={handleSlugBlur}
          />
        )}
        {selectedTab === 'layer' && (
          <LayerConfig
            form={form}
            existingLayers={layers}
            postgisDatasinks={postgisDatasinks}
            postGisDatastructures={postgisDatastructures}
            onSelectLayer={handleSelectLayer}
            onAddLayer={handleAddLayer}
          />
        )}
        {selectedTab === 'styles' && (
          <div data-testid={`tabPlaceholder-${selectedTab}`} className="py-12 text-center text-muted-foreground">
            {t('tabs.placeholder')}
          </div>
        )}
      </Form>
    </ApiConfigWrapper>
  )
}
