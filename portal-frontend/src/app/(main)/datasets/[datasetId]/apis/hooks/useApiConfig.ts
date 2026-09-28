'use client'

import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { FocusEvent, FormEvent, useCallback, useState } from 'react'
import { Path, UseFormReturn } from 'react-hook-form'
import { toast } from 'sonner'

import { useCreateNamedApi, usePatchDataset } from '@/app/services/api/datasets/clientRequests'
import { useCreateLayer, useUpdateLayer } from '@/app/services/api/datasets/layers/clientRequests'
import { useCreateStyle, useUpdateStyle } from '@/app/services/api/datasets/styles/clientRequests'
import { useDatasetPermissions } from '@/hooks/use-dataset-permissions'
import { useError } from '@/hooks/use-error'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { Dataset } from '@/types/datasets'
import { LayerApiPayload, LayerFormData } from '@/types/layers'
import { API_TYPE_QUERY, NamedApi, NamedApiPayload, OwsApiFormData, StaApiFormData } from '@/types/namedApis'
import { StyleFormData } from '@/types/styles'
import { isLayerNameError, isNotDraftError, isSagaInFlightError, LayerSaveError } from '@/utils/errors'
import { hasDirtyField } from '@/utils/form'
import { buildOwsPayload, buildStaPayloadData, mapFormLayerToPayload, mapFormStyleToPayload } from '@/utils/namedApis'

type FormData = StaApiFormData | OwsApiFormData

interface UseApiConfigActionsArgs<TFormData extends FormData> {
  form: UseFormReturn<TFormData>
  dataset: Dataset
  existingApi?: NamedApi
  otherNamedApis: NamedApi[]
  initialSlug: string
  initialFormData?: TFormData
  onAfterDiscard?: () => void
}

const isOwsFormData = (data: StaApiFormData | OwsApiFormData) => data.type === API_TYPE_QUERY.OWS

const buildPayloadData = (data: FormData) => {
  if (isOwsFormData(data)) return buildOwsPayload(data)
  return buildStaPayloadData(data)
}

export const useApiConfig = <TFormData extends FormData>({
  form,
  dataset,
  existingApi,
  otherNamedApis,
  initialSlug,
  initialFormData,
  onAfterDiscard,
}: UseApiConfigActionsArgs<TFormData>) => {
  const t = useTranslations('datasets.overview.completion.apis.config')
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const isCreate = !existingApi
  const { canEditApis } = useDatasetPermissions(dataset)
  const [isEditing, setIsEditing] = useState(isCreate || searchParams.get('mode') === 'edit')
  const isReadOnly = !canEditApis || !isEditing
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)
  const [urlPreviewSlug, setUrlPreviewSlug] = useState(initialSlug)

  const createNamedApi = useCreateNamedApi()
  const updateDataset = usePatchDataset()
  const createLayer = useCreateLayer()
  const updateLayer = useUpdateLayer()
  const createStyle = useCreateStyle()
  const updateStyle = useUpdateStyle()
  const { handleFormValidationError } = useError()
  const isLoading =
    createNamedApi.isPending ||
    updateDataset.isPending ||
    createLayer.isPending ||
    updateLayer.isPending ||
    createStyle.isPending ||
    updateStyle.isPending

  const isDirty = form.formState.isDirty
  const dirtyFields = form.formState.dirtyFields
  const isValid = form.formState.isValid

  const updateMode = useCallback(
    (isEditing: boolean) => {
      setIsEditing(isEditing)
      const params = new URLSearchParams(searchParams.toString())
      if (isEditing) {
        params.set('mode', 'edit')
      } else {
        params.delete('mode')
      }
      const query = params.toString()
      router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false })
    },
    [pathname, router, searchParams],
  )

  const handleSaveBaseInfo = async (data: TFormData) => {
    if (!isCreate && !hasDirtyField(dirtyFields.baseInfo)) return
    const newApi = buildPayloadData(data)
    const redirectUrl = `/datasets/${dataset.id}/apis/${newApi.slug}`

    setUrlPreviewSlug(newApi.slug)

    if (isCreate) {
      await createNamedApi.mutateAsync({
        datasetId: dataset.id,
        api: newApi,
        existingApis: otherNamedApis,
      })
      toast.success(t('messages.createSuccess'))
      router.replace(`${redirectUrl}?mode=edit`)
      router.refresh()
    } else {
      const otherInputs: NamedApiPayload[] = otherNamedApis.map(a => ({
        name: a.name,
        slug: a.slug,
        standard: a.standard,
        version: a.version,
        description: a.description,
      }))
      await updateDataset.mutateAsync({ id: dataset.id, namedApis: [...otherInputs, newApi] })
      toast.success(t('messages.updateSuccess'))
      form.reset(data)
      updateMode(false)
      if (newApi.slug !== initialSlug) {
        router.push(redirectUrl)
      }
      router.refresh()
    }
  }

  const handleSaveLayers = async (data: OwsApiFormData) => {
    const owsDirtyFields = dirtyFields as Partial<Record<keyof OwsApiFormData, unknown>>
    if (!hasDirtyField(owsDirtyFields.layers)) return

    const isNew = (layer: LayerFormData) => layer.id.startsWith('new-')

    const isDirtyOrNew = (layer: LayerFormData, i: number) =>
      isNew(layer) || hasDirtyField(Array.isArray(owsDirtyFields.layers) ? owsDirtyFields.layers[i] : undefined)

    const layersToSave = data.layers.filter(isDirtyOrNew)
    if (layersToSave.length === 0) return

    const payloads: LayerApiPayload[] = mapFormLayerToPayload(layersToSave)
    await Promise.all(
      layersToSave.map(async (layer, i) => {
        try {
          if (isNew(layer)) {
            await createLayer.mutateAsync({ datasetId: dataset.id, data: payloads[i] })
          } else {
            await updateLayer.mutateAsync({ datasetId: dataset.id, layerId: layer.id, data: payloads[i] })
          }
        } catch (error) {
          throw new LayerSaveError(error, data.layers.indexOf(layer))
        }
      }),
    )

    if (layersToSave.some(isNew)) toast.success(t('messages.createLayerSuccess'))
    if (layersToSave.some(l => !isNew(l))) toast.success(t('messages.updateLayerSuccess'))
  }

  const handleSaveStyles = async (data: OwsApiFormData) => {
    const owsDirtyFields = dirtyFields as Partial<Record<keyof OwsApiFormData, unknown>>
    if (!hasDirtyField(owsDirtyFields.styles)) return

    const isNew = (style: StyleFormData) => style.id.startsWith('new-')

    const isDirtyOrNew = (style: StyleFormData, i: number) =>
      isNew(style) || hasDirtyField(Array.isArray(owsDirtyFields.styles) ? owsDirtyFields.styles[i] : undefined)

    const stylesToSave = data.styles.filter(isDirtyOrNew)
    if (stylesToSave.length === 0) return

    await Promise.all(
      stylesToSave.map(async style => {
        const payload = mapFormStyleToPayload(style)
        if (isNew(style)) {
          await createStyle.mutateAsync({ datasetId: dataset.id, style: payload })
        } else {
          await updateStyle.mutateAsync({ datasetId: dataset.id, stilId: style.id, style: payload })
        }
      }),
    )

    if (stylesToSave.some(isNew)) toast.success(t('messages.createStyleSuccess'))
    if (stylesToSave.some(s => !isNew(s))) toast.success(t('messages.updateStyleSuccess'))
  }

  const handleLayerNameError = (error: LayerSaveError) => {
    const layerName = (form.getValues(`layers.${error.layerIndex}.layerName` as Path<TFormData>) as string) ?? ''
    toast.error(t('messages.layerNameExists', { name: layerName }))
    form.setError(`layers.${error.layerIndex}.layerName` as Path<TFormData>, {
      type: 'manual',
      message: t('messages.layerNameExists', { name: layerName }),
    })
  }

  const handleSave = async (): Promise<boolean> => {
    if (isReadOnly) return false
    let isSaved = false
    await form.handleSubmit(
      async data => {
        try {
          await handleSaveBaseInfo(data)
          if (isOwsFormData(data)) {
            await handleSaveLayers(data)
            await handleSaveStyles(data)
          }
          isSaved = true
        } catch (error) {
          const cause = error instanceof LayerSaveError ? error.originalError : error

          if (error instanceof LayerSaveError && isLayerNameError(cause)) {
            handleLayerNameError(error)
          } else if (isNotDraftError(cause)) {
            toast.error(t('messages.notDraftError'))
          } else if (isSagaInFlightError(cause)) {
            toast.error(t('messages.sagaInFlightError'))
          } else {
            toast.error(t('messages.saveError'))
          }
        }
      },
      errors => {
        handleFormValidationError(errors)
      },
    )()
    return isSaved
  }

  const handleExit = () => {
    if (isDirty && isValid) {
      setIsExitModalOpen(true)
      return
    }
    if (isCreate) {
      router.push(`/datasets/${dataset.id}`)
    } else {
      form.reset(initialFormData)
      updateMode(false)
    }
  }

  const handleDiscardAndExit = () => {
    setIsExitModalOpen(false)
    if (isCreate) {
      router.push(`/datasets/${dataset.id}`)
      return
    }
    form.reset(initialFormData)
    onAfterDiscard?.()
    setUrlPreviewSlug(initialSlug)
    updateMode(false)
  }

  const handleSaveAndExit = () => {
    void handleSave().then(isSaved => {
      if (isSaved) setIsExitModalOpen(false)
    })
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    void handleSave()
  }

  const handleSlugBlur = (_event: FocusEvent<HTMLInputElement>) => {
    setUrlPreviewSlug(form.getValues('baseInfo.slug' as Path<TFormData>) as string)
  }

  useRegisterUnsavedChanges(isDirty, handleSave)

  return {
    isReadOnly,
    isLoading,
    isExitModalOpen,
    setIsExitModalOpen,
    urlPreviewSlug,
    updateMode,
    handleSave,
    handleSlugBlur,
    handleExit,
    handleDiscardAndExit,
    handleSaveAndExit,
    handleSubmit,
  }
}
