'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQueries } from '@tanstack/react-query'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { FieldPath, useFieldArray, useForm, UseFormReturn } from 'react-hook-form'
import { toast } from 'sonner'

import { useGetDatasinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import { useDeleteLayer, useGetLayers } from '@/app/services/api/datasets/layers/clientRequests'
import { useGetStyles } from '@/app/services/api/datasets/styles/clientRequests'
import { apiRequest } from '@/app/services/api/request/apiRequest'
import { ContentCard } from '@/components/content-card/ContentCard'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
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
import { getNativeCRSFromDatasink, mapApiLayerToFormData } from '@/utils/namedApis'

import { useApiConfig } from '../../hooks/useApiConfig'
import { ApiConfigTab, ApiConfigWrapper } from '../ApiConfigWrapper'
import { BaseInfoForm } from '../base-info/BaseInfoForm'
import { LayerConfig } from './LayerConfig'

const tabs = [
  { value: 'basicInfo' as ApiConfigTab, label: 'datasets.overview.completion.apis.config.tabs.basicInfo' },
  { value: 'layer' as ApiConfigTab, label: 'datasets.overview.completion.apis.config.tabs.layer' },
  { value: 'styles' as ApiConfigTab, label: 'datasets.overview.completion.apis.config.tabs.styles' },
]

const defaultLayer: LayerFormData = {
  id: '',
  title: '',
  layerName: '',
  description: '',
  dataSinkId: '',
  attribute: [],
  cqlFilter: '',
  geometryColumnRef: '',
  nativeCRS: '',
  crs: '',
  bboxAutoCalculate: false,
  nativeBoundingBox: { minX: '', minY: '', maxX: '', maxY: '', crs: '' },
  latLonBoundingBox: { minX: '', minY: '', maxX: '', maxY: '', crs: 'EPSG:4326' },
  defaultStyleId: '',
  alternativeStyleIds: [],
}

interface WfsWmsApiConfigPageProps {
  dataset: Dataset
  existingApi?: NamedApi
  testId?: string
}

export const WfsWmsApiConfigPage = ({ dataset, existingApi, testId }: WfsWmsApiConfigPageProps) => {
  const t = useTranslations('datasets.overview.completion.apis.config')
  const apiType = API_TYPE_QUERY.WFS_WMS
  const typeLabel = t(`title.${apiType}`)

  const defaults = DEFAULTS_BY_TYPE[apiType]

  const [selectedTab, setSelectedTab] = useState<ApiConfigTab>('basicInfo')

  const otherNamedApis = useMemo(
    () => (dataset.namedApis ?? []).filter(a => a.slug !== existingApi?.slug),
    [dataset.namedApis, existingApi?.slug],
  )
  const existingSlugs = useMemo(() => otherNamedApis.map(a => a.slug), [otherNamedApis])
  const formSchema = useMemo(() => WfsWmsApiFormSchema({ existingSlugs }), [existingSlugs])
  const initialSlug = existingApi?.slug ?? defaults.defaultSlug

  const { data: datasinksData, isPending: isDatasinksLoading } = useGetDatasinks(dataset.id)
  const { data: layersData, isPending: isLayersLoading } = useGetLayers(dataset.id)
  const { data: stylesData } = useGetStyles(dataset.id)

  const [selectedLayerIndex, setSelectedLayerIndex] = useState<number | null>(null)

  const postgisDatasinks = useMemo(
    () => (datasinksData?.data || []).filter(datasink => datasink.dataSinkType === DATASINK_TYPES.POSTGIS),
    [datasinksData],
  )
  const datastructuresToFetch = postgisDatasinks?.map(datasink => ({
    datastructureId: datasink.configuration.dataStructureVersion.dataStructureId,
    versionId: datasink.configuration.dataStructureVersion.id,
  }))

  const postgisDatastructureQueries = useQueries({
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
    combine: results => ({
      data: results.filter(r => !!r.data).map(r => r.data.data),
      isPending: results.some(r => r.isPending),
    }),
  })
  const postgisDatastructures = postgisDatastructureQueries.data
  const isDataLoading = isLayersLoading || isDatasinksLoading || postgisDatastructureQueries.isPending

  const layers = useMemo(
    () =>
      mapApiLayerToFormData(layersData?.data || []).map(layer => {
        // setting the nativeLayer here is necessary since in the API response it gets returned as null
        // TODO: remove this section once this is fixed in the backend
        if (!layer.nativeCRS && layer.dataSinkId) {
          const nativeCRS = getNativeCRSFromDatasink(layer.dataSinkId, postgisDatasinks, postgisDatastructures)
          return { ...layer, nativeCRS }
        }
        return layer
      }),
    [layersData, postgisDatasinks, postgisDatastructures],
  )

  const wfsWmsDefaults: WfsWmsApiFormData = {
    type: API_TYPE_QUERY.WFS_WMS,
    baseInfo: {
      name: existingApi?.name ?? '',
      slug: initialSlug,
      description: existingApi?.description ?? '',
      persistence: defaults.persistenceValue,
    },
    layers,
  }

  const form = useForm<WfsWmsApiFormData>({
    resolver: zodResolver(formSchema),
    mode: 'onChange',
    defaultValues: wfsWmsDefaults,
  })

  const { fields, append, remove } = useFieldArray({ control: form.control, name: 'layers', keyName: '_key' })

  useEffect(() => {
    form.reset(wfsWmsDefaults)
    setSelectedLayerIndex(layers.length > 0 ? 0 : null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [existingApi?.id, layers])

  const baseInfoValues = form.watch('baseInfo')
  const isBaseInfoValid = formSchema.shape.baseInfo.safeParse(baseInfoValues).success

  const layersValues = form.watch('layers')
  const isLayersValid = formSchema.shape.layers.safeParse(layersValues).success

  const completedTabs: ApiConfigTab[] = [
    ...(isBaseInfoValid ? (['basicInfo'] as ApiConfigTab[]) : []),
    ...(isLayersValid ? (['layer'] as ApiConfigTab[]) : []),
  ]

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
    initialFormData: wfsWmsDefaults,
    onAfterDiscard: () => setSelectedLayerIndex(layers.length > 0 ? 0 : null),
  })

  const handleSelectLayer = (index: number) => {
    setSelectedLayerIndex(index)
  }

  const handleAddLayer = () => {
    const newIndex = fields.length
    append({ ...defaultLayer, id: `new-${crypto.randomUUID()}` })
    setSelectedLayerIndex(newIndex)
  }

  const deleteLayer = useDeleteLayer()

  const handleDeleteLayer = async () => {
    if (selectedLayerIndex === null) return
    const layer = fields[selectedLayerIndex]
    const isNew = layer.id.startsWith('new-')
    if (!isNew) {
      try {
        await deleteLayer.mutateAsync({ datasetId: dataset.id, layerId: layer.id })
        toast.success(t('messages.deleteLayerSuccess'))
      } catch {
        toast.error(t('messages.deleteLayerError'))
        throw new Error()
      }
    }
    remove(selectedLayerIndex)
    const remaining = fields.length - 1
    setSelectedLayerIndex(remaining === 0 ? null : Math.min(selectedLayerIndex, remaining - 1))
  }

  const handleTableChange = (datasinkId: string) => {
    if (selectedLayerIndex === null) return
    form.setValue(`layers.${selectedLayerIndex}.dataSinkId` as FieldPath<WfsWmsApiFormData>, datasinkId, {
      shouldDirty: true,
    })
    const nativeCRS = getNativeCRSFromDatasink(datasinkId, postgisDatasinks, postgisDatastructures)
    form.setValue(`layers.${selectedLayerIndex}.nativeCRS` as FieldPath<WfsWmsApiFormData>, nativeCRS, {
      shouldDirty: true,
    })
    if (nativeCRS) {
      form.setValue(`layers.${selectedLayerIndex}.crs` as FieldPath<WfsWmsApiFormData>, nativeCRS, {
        shouldDirty: true,
      })
    }
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
      {isDataLoading ? (
        <LoadingSpinner />
      ) : (
        <Form {...form}>
          {selectedTab === 'basicInfo' && (
            <ContentCard>
              <BaseInfoForm
                form={form as unknown as UseFormReturn<StaApiFormData>}
                apiType={apiType}
                isReadOnly={isReadOnly}
                datasetId={dataset.id}
                typeLabel={typeLabel}
                urlPreviewSlug={urlPreviewSlug}
                onSlugBlur={handleSlugBlur}
              />
            </ContentCard>
          )}
          {selectedTab === 'layer' && (
            <LayerConfig
              form={form}
              existingLayers={fields}
              styles={stylesData?.data || []}
              postgisDatasinks={postgisDatasinks}
              postGisDatastructures={postgisDatastructures}
              selectedLayerIndex={selectedLayerIndex}
              isReadOnly={isReadOnly}
              isDeleteLayerLoading={deleteLayer.isPending}
              onSelectLayer={handleSelectLayer}
              onAddLayer={handleAddLayer}
              onDeleteLayer={handleDeleteLayer}
              onTableChange={handleTableChange}
            />
          )}
          {selectedTab === 'styles' && (
            <ContentCard>
              <div data-testid={`tabPlaceholder-${selectedTab}`} className="py-12 text-center text-muted-foreground">
                {t('tabs.placeholder')}
              </div>
            </ContentCard>
          )}
        </Form>
      )}
    </ApiConfigWrapper>
  )
}
