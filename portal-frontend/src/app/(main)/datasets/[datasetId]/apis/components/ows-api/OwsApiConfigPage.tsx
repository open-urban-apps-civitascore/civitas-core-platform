'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQueries } from '@tanstack/react-query'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { FieldPath, useFieldArray, useForm, UseFormReturn } from 'react-hook-form'
import { toast } from 'sonner'

import { useGetDataSinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import { useDeleteLayer, useGetLayers } from '@/app/services/api/datasets/layers/clientRequests'
import { useDeleteStyle, useGetStyles } from '@/app/services/api/datasets/styles/clientRequests'
import { apiRequest } from '@/app/services/api/request/apiRequest'
import { ContentCard } from '@/components/content-card/ContentCard'
import { Form } from '@/components/ui/form'
import { Dataset } from '@/types/datasets'
import { DATASINK_TYPES } from '@/types/datasinks'
import { DatastructureVersion } from '@/types/datastructures'
import { LayerFormData } from '@/types/layers'
import {
  API_TYPE_QUERY,
  DEFAULTS_BY_TYPE,
  NamedApi,
  OwsApiFormData,
  OwsApiFormSchema,
  StaApiFormData,
} from '@/types/namedApis'
import { StyleFormData } from '@/types/styles'
import { isNotDraftError, isResourceInUseError, isSagaInFlightError } from '@/utils/errors'
import { getNativeCRSFromDataSink, mapApiLayerToFormData, mapApiStyleToFormData } from '@/utils/namedApis'

import { useApiConfig } from '../../hooks/useApiConfig'
import { ApiConfigTab, ApiConfigWrapper } from '../ApiConfigWrapper'
import { BaseInfoForm } from '../base-info/BaseInfoForm'
import { LayerConfig } from './LayerConfig'
import { StylesConfig } from './StylesConfig'

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
  keywords: [],
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

const defaultStyle: StyleFormData = {
  id: '',
  name: '',
  sldContent: '',
}

interface OwsApiConfigPageProps {
  dataset: Dataset
  existingApi?: NamedApi
  testId?: string
}

export const OwsApiConfigPage = ({ dataset, existingApi, testId }: OwsApiConfigPageProps) => {
  const t = useTranslations('datasets.overview.completion.apis.config')
  const apiType = API_TYPE_QUERY.OWS
  const typeLabel = t(`title.${apiType}`)

  const defaults = DEFAULTS_BY_TYPE[apiType]

  const [selectedTab, setSelectedTab] = useState<ApiConfigTab>('basicInfo')

  const otherNamedApis = useMemo(
    () => (dataset.namedApis ?? []).filter(a => a.slug !== existingApi?.slug),
    [dataset.namedApis, existingApi?.slug],
  )
  const existingSlugs = useMemo(() => otherNamedApis.map(a => a.slug), [otherNamedApis])
  const formSchema = useMemo(() => OwsApiFormSchema({ existingSlugs }), [existingSlugs])
  const initialSlug = existingApi?.slug ?? defaults.defaultSlug

  const { data: dataSinksData, isPending: isDataSinksLoading } = useGetDataSinks(dataset.id)
  const { data: layersData, isPending: isLayersLoading } = useGetLayers(dataset.id)
  const { data: stylesData, isPending: isStylesLoading } = useGetStyles(dataset.id)

  const postgisDataSinks = useMemo(() => {
    const currentPipelineIds = dataset.pipelines.map(pipeline => pipeline.id)
    return (dataSinksData?.data || []).filter(
      dataSink => dataSink.dataSinkType === DATASINK_TYPES.POSTGIS && currentPipelineIds.includes(dataSink.pipelineId),
    )
  }, [dataSinksData, dataset.pipelines])

  const datastructuresToFetch = postgisDataSinks?.map(dataSink => ({
    datastructureId: dataSink.configuration.dataStructureVersion.dataStructureId,
    versionId: dataSink.configuration.dataStructureVersion.id,
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
  const isDataLoading =
    isLayersLoading || isStylesLoading || isDataSinksLoading || postgisDatastructureQueries.isPending

  const layers = useMemo(
    () =>
      mapApiLayerToFormData(layersData?.data || []).map(layer => {
        // setting the nativeLayer here is necessary since in the API response it gets returned as null
        // TODO: remove this section once this is fixed in the backend
        if (!layer.nativeCRS && layer.dataSinkId) {
          const nativeCRS = getNativeCRSFromDataSink(layer.dataSinkId, postgisDataSinks, postgisDatastructures)
          return { ...layer, nativeCRS }
        }
        return layer
      }),
    [layersData, postgisDataSinks, postgisDatastructures],
  )
  const apiStyles = useMemo(() => stylesData?.data || [], [stylesData])
  const styleFormData = useMemo(() => mapApiStyleToFormData(apiStyles), [apiStyles])

  const [selectedLayerIndex, setSelectedLayerIndex] = useState<number | null>(layers.length > 0 ? 0 : null)
  const [selectedStyleIndex, setSelectedStyleIndex] = useState<number | null>(styleFormData.length > 0 ? 0 : null)

  const owsDefaults: OwsApiFormData = {
    type: API_TYPE_QUERY.OWS,
    baseInfo: {
      name: existingApi?.name ?? '',
      slug: initialSlug,
      description: existingApi?.description ?? '',
      persistence: defaults.persistenceValue,
    },
    layers,
    styles: styleFormData,
  }

  const form = useForm<OwsApiFormData>({
    resolver: zodResolver(formSchema),
    mode: 'onChange',
    defaultValues: owsDefaults,
  })

  const {
    fields: layerFields,
    append: appendLayer,
    remove: removeLayer,
  } = useFieldArray({ control: form.control, name: 'layers', keyName: '_key' })

  const {
    fields: styleFields,
    append: appendStyle,
    remove: removeStyle,
  } = useFieldArray({ control: form.control, name: 'styles', keyName: '_key' })

  useEffect(() => {
    form.reset(owsDefaults)
    setSelectedLayerIndex(layers.length > 0 ? 0 : null)
    setSelectedStyleIndex(styleFormData.length > 0 ? 0 : null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [existingApi?.id, layers, apiStyles])

  const baseInfoValues = form.watch('baseInfo')
  const isBaseInfoValid = formSchema.shape.baseInfo.safeParse(baseInfoValues).success

  const layersValues = form.watch('layers')
  const isLayersValid = layersValues.length > 0 && formSchema.shape.layers.safeParse(layersValues).success

  const stylesValues = form.watch('styles')
  const isStylesValid = formSchema.shape.styles.safeParse(stylesValues).success

  const completedTabs: ApiConfigTab[] = [
    ...(isBaseInfoValid ? (['basicInfo'] as ApiConfigTab[]) : []),
    ...(isLayersValid ? (['layer'] as ApiConfigTab[]) : []),
    ...(isStylesValid ? (['styles'] as ApiConfigTab[]) : []),
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
    initialFormData: owsDefaults,
    onAfterDiscard: () => {
      setSelectedLayerIndex(layers.length > 0 ? 0 : null)
      setSelectedStyleIndex(styleFormData.length > 0 ? 0 : null)
    },
  })

  const handleSelectLayer = (index: number) => {
    setSelectedLayerIndex(index)
  }

  const handleAddLayer = () => {
    if (isReadOnly) return
    const newIndex = layerFields.length
    appendLayer({ ...defaultLayer, id: `new-${crypto.randomUUID()}` })
    setSelectedLayerIndex(newIndex)
  }

  const deleteLayer = useDeleteLayer()

  const handleDeleteLayer = async () => {
    if (isReadOnly || selectedLayerIndex === null) return
    const layer = layerFields[selectedLayerIndex]
    const isNew = layer.id.startsWith('new-')
    if (!isNew) {
      try {
        await deleteLayer.mutateAsync({ datasetId: dataset.id, layerId: layer.id })
        toast.success(t('messages.deleteLayerSuccess'))
      } catch (error) {
        if (isNotDraftError(error)) {
          toast.error(t('messages.notDraftError'))
        } else if (isSagaInFlightError(error)) {
          toast.error(t('messages.sagaInFlightError'))
        } else {
          toast.error(t('messages.deleteLayerError'))
        }
        throw new Error()
      }
    }
    removeLayer(selectedLayerIndex)
    const remaining = layerFields.length - 1
    setSelectedLayerIndex(remaining === 0 ? null : Math.min(selectedLayerIndex, remaining - 1))
  }

  const handleSelectStyle = (index: number) => {
    setSelectedStyleIndex(index)
  }

  const handleAddStyle = () => {
    if (isReadOnly) return
    const newIndex = styleFields.length
    appendStyle({ ...defaultStyle, id: `new-${crypto.randomUUID()}` })
    setSelectedStyleIndex(newIndex)
  }

  const deleteStyle = useDeleteStyle()

  const handleDeleteStyle = async () => {
    if (isReadOnly || selectedStyleIndex === null) return
    const style = styleFields[selectedStyleIndex]
    const isNew = style.id.startsWith('new-')
    if (!isNew) {
      try {
        await deleteStyle.mutateAsync({ datasetId: dataset.id, stilId: style.id })
        toast.success(t('messages.deleteStyleSuccess'))
      } catch (error) {
        if (isNotDraftError(error)) {
          toast.error(t('messages.notDraftError'))
        } else if (isSagaInFlightError(error)) {
          toast.error(t('messages.sagaInFlightError'))
        } else if (isResourceInUseError(error)) {
          toast.error(t('messages.styleInUseError'))
        } else {
          toast.error(t('messages.deleteStyleError'))
        }
        throw new Error()
      }
    }
    removeStyle(selectedStyleIndex)
    const remaining = styleFields.length - 1
    setSelectedStyleIndex(remaining === 0 ? null : Math.min(selectedStyleIndex, remaining - 1))
  }

  const handleTableChange = (dataSinkId: string) => {
    if (selectedLayerIndex === null) return
    form.setValue(`layers.${selectedLayerIndex}.dataSinkId` as FieldPath<OwsApiFormData>, dataSinkId, {
      shouldDirty: true,
    })
    const nativeCRS = getNativeCRSFromDataSink(dataSinkId, postgisDataSinks, postgisDatastructures)
    form.setValue(`layers.${selectedLayerIndex}.nativeCRS` as FieldPath<OwsApiFormData>, nativeCRS, {
      shouldDirty: true,
    })
    if (nativeCRS) {
      form.setValue(`layers.${selectedLayerIndex}.crs` as FieldPath<OwsApiFormData>, nativeCRS, {
        shouldDirty: true,
      })
    }
  }

  return (
    <ApiConfigWrapper
      dataset={dataset}
      isReadOnly={isReadOnly}
      hasUnsavedChanges={form.formState.isDirty}
      isFormValid={form.formState.isValid}
      isLoading={isLoading || isDataLoading}
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
            styles={apiStyles}
            postgisDataSinks={postgisDataSinks}
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
          <StylesConfig
            form={form}
            existingStyles={styleFields}
            selectedStyleIndex={selectedStyleIndex}
            isReadOnly={isReadOnly}
            isDeleteStyleLoading={deleteStyle.isPending}
            onSelectStyle={handleSelectStyle}
            onAddStyle={handleAddStyle}
            onDeleteStyle={handleDeleteStyle}
          />
        )}
      </Form>
    </ApiConfigWrapper>
  )
}
