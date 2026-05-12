'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { FocusEvent, FormEvent, useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { usePatchDataset } from '@/app/services/api/datasets/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Tab } from '@/components/segmented-control-bar/SegmentedControlBar'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { useError } from '@/hooks/use-error'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { cn } from '@/lib/utils'
import { Dataset } from '@/types/datasets'
import {
  API_TYPE_QUERY,
  ApiTypeQuery,
  buildNamedApiFormSchema,
  DEFAULTS_BY_TYPE,
  NamedApi,
  NamedApiFormData,
  NamedApiInput,
} from '@/types/namedApis'

import { ApiConfigForm } from './ApiConfigForm'

type ApiConfigTab = 'basicInfo' | 'layer' | 'styles'

interface ApiConfigPageProps {
  dataset: Dataset
  apiType: ApiTypeQuery
  existingApi?: NamedApi
  testId?: string
}

export const ApiConfigPage = (props: ApiConfigPageProps) => {
  const { dataset, apiType, existingApi, testId } = props
  const t = useTranslations('datasets.overview.completion.apis.config')
  const tCommon = useTranslations('common')

  const router = useRouter()
  const searchParams = useSearchParams()
  const mode = searchParams.get('mode')

  const isCreate = !existingApi
  const [isReadOnly, setIsReadOnly] = useState(isCreate ? false : mode !== 'edit')
  const [selectedTab, setSelectedTab] = useState<ApiConfigTab>('basicInfo')
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  const defaults = DEFAULTS_BY_TYPE[apiType]
  const { handleFormValidationError } = useError()
  const updateDataset = usePatchDataset()
  const isLoading = updateDataset.isPending

  const otherNamedApis = useMemo(
    () => (dataset.namedApis ?? []).filter(a => a.id !== existingApi?.id),
    [dataset.namedApis, existingApi?.id],
  )
  const existingSlugs = useMemo(() => otherNamedApis.map(a => a.slug), [otherNamedApis])

  const formSchema = useMemo(() => buildNamedApiFormSchema({ existingSlugs }), [existingSlugs])

  const initialSlug = existingApi?.slug ?? defaults.defaultSlug
  const initialPersistence = defaults.persistenceValue

  const form = useForm<NamedApiFormData>({
    resolver: zodResolver(formSchema),
    mode: 'onChange',
    defaultValues: {
      name: existingApi?.name ?? '',
      slug: initialSlug,
      description: existingApi?.description ?? '',
      persistence: initialPersistence,
    },
  })

  const [urlPreviewSlug, setUrlPreviewSlug] = useState(initialSlug)

  useEffect(() => {
    form.reset({
      name: existingApi?.name ?? '',
      slug: initialSlug,
      description: existingApi?.description ?? '',
      persistence: initialPersistence,
    })
    setUrlPreviewSlug(initialSlug)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [existingApi?.id])

  const hasUnsavedChanges = form.formState.isDirty
  const isFormValid = form.formState.isValid

  const typeLabel = t(`title.${apiType}`)

  const tabs: Tab<ApiConfigTab>[] = useMemo(() => {
    if (apiType === API_TYPE_QUERY.WFS_WMS) {
      return [
        { value: 'basicInfo', label: 'datasets.overview.completion.apis.config.tabs.basicInfo' },
        { value: 'layer', label: 'datasets.overview.completion.apis.config.tabs.layer' },
        { value: 'styles', label: 'datasets.overview.completion.apis.config.tabs.styles' },
      ]
    }
    return [{ value: 'basicInfo', label: 'datasets.overview.completion.apis.config.tabs.basicInfo' }]
  }, [apiType])

  const completedTabs: ApiConfigTab[] = isFormValid ? ['basicInfo'] : []

  const buildNamedApiPayload = (data: NamedApiFormData): NamedApiInput => ({
    name: data.name.trim(),
    slug: data.slug,
    standard: defaults.standard,
    description: data.description?.trim() || undefined,
  })

  const handleSlugBlur = (_event: FocusEvent<HTMLInputElement>) => {
    setUrlPreviewSlug(form.getValues('slug'))
  }

  const handleSave = async (): Promise<boolean> => {
    let isSaved = false
    await form.handleSubmit(
      async data => {
        try {
          const newApi = buildNamedApiPayload(data)
          const otherInputs: NamedApiInput[] = otherNamedApis.map(a => ({
            name: a.name,
            slug: a.slug,
            standard: a.standard,
            version: a.version,
            description: a.description,
          }))

          await updateDataset.mutateAsync({
            id: dataset.id,
            namedApis: [...otherInputs, newApi],
          })

          if (isCreate) {
            toast.success(t('messages.createSuccess'))
            router.push(`/datasets/${dataset.id}/apis/${newApi.slug}?mode=edit`)
          } else {
            toast.success(t('messages.updateSuccess'))
            router.refresh()
            setIsReadOnly(true)
          }
          isSaved = true
        } catch {
          toast.error(t('messages.saveError'))
          isSaved = false
        }
      },
      errors => {
        handleFormValidationError(errors)
        isSaved = false
      },
    )()
    return isSaved
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    void handleSave()
  }

  const handleExit = () => {
    if (hasUnsavedChanges) {
      setIsExitModalOpen(true)
      return
    }
    if (isCreate) {
      router.push(`/datasets/${dataset.id}`)
    } else {
      setIsReadOnly(true)
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
    setIsReadOnly(true)
  }

  const handleSaveAndExit = () => {
    void handleSave().then(isSaved => {
      if (isSaved) setIsExitModalOpen(false)
    })
  }

  useRegisterUnsavedChanges(hasUnsavedChanges, handleSave)

  const buttonGroup = isReadOnly ? (
    <div className="flex items-center gap-2 px-[var(--layout-padding)]">
      <Button data-testid="editButton" type="button" onClick={() => setIsReadOnly(false)}>
        {tCommon('actions.edit')}
      </Button>
    </div>
  ) : (
    <div className="flex items-center gap-2 px-[var(--layout-padding)]">
      <Button data-testid="cancelButton" type="button" variant="secondary" onClick={handleExit} disabled={isLoading}>
        {tCommon('actions.cancel')}
      </Button>
      <Button
        data-testid="confirmButton"
        type="submit"
        form="api-config-form"
        disabled={!hasUnsavedChanges || !isFormValid || isLoading}
      >
        {tCommon('actions.submit')}
      </Button>
    </div>
  )

  return (
    <PageContainer testId={testId} headerType="withSubTabsOrSubtitle" className="overflow-auto">
      <PageHeader
        title={typeLabel}
        badgeTitle={t('protectedBadge')}
        customElement={buttonGroup}
        segmentedControlBarProps={{
          tabs,
          selectedTab,
          onTabChange: setSelectedTab,
          completedTabs,
          tabsWithNoCompletionStatus: ['styles'],
          hasCompletionStatus: true,
        }}
      />

      <PageBackground className="overflow-y-auto" hasBackground={!isReadOnly}>
        <Form {...form}>
          <form
            id="api-config-form"
            data-testid="apiConfigForm"
            aria-label={`${tCommon('form')} ${typeLabel}`}
            onSubmit={handleSubmit}
            className="h-full"
          >
            <ContentCard className={cn('h-auto')}>
              {selectedTab === 'basicInfo' && (
                <ApiConfigForm
                  form={form}
                  apiType={apiType}
                  isReadOnly={isReadOnly}
                  datasetId={dataset.id}
                  typeLabel={typeLabel}
                  urlPreviewSlug={urlPreviewSlug}
                  onSlugBlur={handleSlugBlur}
                />
              )}
              {(selectedTab === 'layer' || selectedTab === 'styles') && (
                <div data-testid={`tabPlaceholder-${selectedTab}`} className="py-12 text-center text-muted-foreground">
                  {t('tabs.placeholder')}
                </div>
              )}
            </ContentCard>
          </form>
        </Form>
      </PageBackground>

      <ExitWarningModal
        open={isExitModalOpen}
        onOpenChange={setIsExitModalOpen}
        onDiscard={handleDiscardAndExit}
        onConfirm={handleSaveAndExit}
        isLoading={isLoading}
      />
    </PageContainer>
  )
}
