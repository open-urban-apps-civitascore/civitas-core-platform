'use client'

import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { FocusEvent, FormEvent, useCallback, useState } from 'react'
import { UseFormReturn } from 'react-hook-form'
import { toast } from 'sonner'

import { useCreateNamedApi, usePatchDataset } from '@/app/services/api/datasets/clientRequests'
import { useError } from '@/hooks/use-error'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { Dataset } from '@/types/datasets'
import { API_TYPE_QUERY, NamedApi, NamedApiPayload, StaApiFormData, WfsWmsApiFormData } from '@/types/namedApis'
import { buildStaPayloadData, buildWfsWmsPayload } from '@/utils/namedApis'

type FormData = StaApiFormData | WfsWmsApiFormData
interface UseApiConfigActionsArgs<TFormData extends FormData> {
  form: UseFormReturn<TFormData>
  dataset: Dataset
  existingApi?: NamedApi
  otherNamedApis: NamedApi[]
  initialSlug: string
  isCreateLayerMode?: boolean
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
  isCreateLayerMode = false,
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
  const { handleFormValidationError } = useError()
  const isLoading = createNamedApi.isPending || updateDataset.isPending
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
    console.log(dirtyFields)
    if (!dirtyFields.baseInfo) return
    const newApi = buildPayloadData(data)
    if (isCreate) {
      await createNamedApi.mutateAsync({
        datasetId: dataset.id,
        api: newApi?.baseInfo,
        existingApis: otherNamedApis,
      })
      toast.success(t('messages.createSuccess'))
      router.push(`/datasets/${dataset.id}/apis/${newApi.baseInfo.slug}?mode=edit`)
    } else {
      const otherInputs: NamedApiPayload[] = otherNamedApis.map(a => ({
        name: a.name,
        slug: a.slug,
        standard: a.standard,
        version: a.version,
        description: a.description,
      }))
      await updateDataset.mutateAsync({ id: dataset.id, namedApis: [...otherInputs, newApi.baseInfo] })
      toast.success(t('messages.updateSuccess'))
      form.reset(data)
      router.refresh()
      updateMode(false)
    }
  }

  const handleSaveLayers = (data: WfsWmsApiFormData) => {
    const wfsDirtyFields = dirtyFields as Partial<Record<keyof WfsWmsApiFormData, unknown>>
    if (!wfsDirtyFields.layer) return
    if (isCreateLayerMode) console.log('create layer')
    console.log('saving layers', data)
  }

  const handleSave = async (): Promise<boolean> => {
    let isSaved = false
    await form.handleSubmit(
      async data => {
        try {
          await handleSaveBaseInfo(data)
          if (isWfsWmsFormData(data)) await handleSaveLayers(data)
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
    form.reset()
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
