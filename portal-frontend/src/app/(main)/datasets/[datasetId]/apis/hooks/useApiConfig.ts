'use client'

import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { FocusEvent, FormEvent, useCallback, useState } from 'react'
import { UseFormReturn } from 'react-hook-form'
import { toast } from 'sonner'

import { useCreateNamedApi, usePatchDataset } from '@/app/services/api/datasets/clientRequests'
import { useCreateLayer, useUpdateLayer } from '@/app/services/api/datasets/layers/clientRequests'
import { useCreateStyle, useUpdateStyle } from '@/app/services/api/styles/clientRequests'
import { useError } from '@/hooks/use-error'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { Dataset } from '@/types/datasets'
import {
  API_TYPE_QUERY,
  LayerApiPayload,
  LayerFormData,
  NamedApi,
  NamedApiPayload,
  StaApiFormData,
  StyleFormData,
  WfsWmsApiFormData,
} from '@/types/namedApis'
import { hasDirtyField } from '@/utils/form'
import {
  buildStaPayloadData,
  buildWfsWmsPayload,
  mapFormLayerToPayload,
  mapFormStyleToPayload,
} from '@/utils/namedApis'

type FormData = StaApiFormData | WfsWmsApiFormData

interface UseApiConfigActionsArgs<TFormData extends FormData> {
  form: UseFormReturn<TFormData>
  dataset: Dataset
  existingApi?: NamedApi
  otherNamedApis: NamedApi[]
  initialSlug: string
  initialFormData?: TFormData
  onAfterDiscard?: () => void
}

const isWfsWmsFormData = (data: StaApiFormData | WfsWmsApiFormData) => data.type === API_TYPE_QUERY.WFS_WMS

const buildPayloadData = (data: FormData) => {
  if (isWfsWmsFormData(data)) return buildWfsWmsPayload(data)
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
  const [isReadOnly, setIsReadOnly] = useState(isCreate ? false : searchParams.get('mode') !== 'edit')
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
    [pathname, router, searchParams],
  )

  const handleSaveBaseInfo = async (data: TFormData) => {
    if (!isCreate && !hasDirtyField(dirtyFields.baseInfo)) return
    const newApi = buildPayloadData(data)
    if (isCreate) {
      await createNamedApi.mutateAsync({
        datasetId: dataset.id,
        api: newApi,
        existingApis: otherNamedApis,
      })
      toast.success(t('messages.createSuccess'))
      router.push(`/datasets/${dataset.id}/apis/${newApi.slug}?mode=edit`)
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
      router.refresh()
      updateMode(false)
    }
  }

  const handleSaveLayers = async (data: WfsWmsApiFormData) => {
    const wfsDirtyFields = dirtyFields as Partial<Record<keyof WfsWmsApiFormData, unknown>>
    if (!hasDirtyField(wfsDirtyFields.layers)) return

    const isNew = (layer: LayerFormData) => layer.id.startsWith('new-')

    const isDirtyOrNew = (layer: LayerFormData, i: number) =>
      isNew(layer) || hasDirtyField(Array.isArray(wfsDirtyFields.layers) ? wfsDirtyFields.layers[i] : undefined)

    const layersToSave = data.layers.filter(isDirtyOrNew)
    if (layersToSave.length === 0) return

    const payloads: LayerApiPayload[] = mapFormLayerToPayload(layersToSave)
    await Promise.all(
      layersToSave.map(async (layer, i) => {
        if (isNew(layer)) {
          await createLayer.mutateAsync({ datasetId: dataset.id, data: payloads[i] })
        } else {
          await updateLayer.mutateAsync({ datasetId: dataset.id, layerId: layer.id, data: payloads[i] })
        }
      }),
    )

    if (layersToSave.some(isNew)) toast.success(t('messages.createLayerSuccess'))
    if (layersToSave.some(l => !isNew(l))) toast.success(t('messages.updateLayerSuccess'))
  }

  const handleSaveStyles = async (data: WfsWmsApiFormData) => {
    const wfsDirtyFields = dirtyFields as Partial<Record<keyof WfsWmsApiFormData, unknown>>
    if (!hasDirtyField(wfsDirtyFields.styles)) return

    const isNew = (style: StyleFormData) => style.id.startsWith('new-')

    const isDirtyOrNew = (style: StyleFormData, i: number) =>
      isNew(style) || hasDirtyField(Array.isArray(wfsDirtyFields.styles) ? wfsDirtyFields.styles[i] : undefined)

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

  const handleSave = async (): Promise<boolean> => {
    let isSaved = false
    await form.handleSubmit(
      async data => {
        try {
          await handleSaveBaseInfo(data)
          if (isWfsWmsFormData(data)) {
            await handleSaveLayers(data)
            await handleSaveStyles(data)
          }
          isSaved = true
        } catch {
          toast.error(t('messages.saveError'))
        }
      },
      errors => {
        handleFormValidationError(errors)
      },
    )()

    return isSaved
  }

  const handleSlugBlur = (_event: FocusEvent<HTMLInputElement>) => {
    setUrlPreviewSlug((form.getValues as (name: 'slug') => string)('slug'))
  }

  const handleExit = () => {
    if (isDirty) {
      setIsExitModalOpen(true)
      return
    }
    if (isCreate) {
      router.push(`/datasets/${dataset.id}`)
    } else {
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
