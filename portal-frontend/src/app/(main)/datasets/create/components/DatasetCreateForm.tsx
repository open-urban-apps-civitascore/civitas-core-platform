'use client'
import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { useCreateDataset } from '@/app/services/api/datasets/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { useError } from '@/hooks/use-error'
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { cn } from '@/lib/utils'
import { SelectOption } from '@/types/common'
import { DatasetCreateFormData, DatasetCreateFormSchema } from '@/types/datasets'
import { usePermissions } from '@/hooks/use-permissions'
import { PERMISSION_NAMES } from '@/types/currentUser'

type DatasetCreateFormProps = {
  datapoolOptions?: SelectOption[]
}

export const DatasetCreateForm = (props: DatasetCreateFormProps) => {
  const { datapoolOptions } = props
  const t = useTranslations('datasets')
  const tCommon = useTranslations('common')
  const { hasPermission } = usePermissions()

  const router = useRouter()
  const searchParams = useSearchParams()

  const canReadDatapools = hasPermission(PERMISSION_NAMES.DATAPOOL_READ)
  const datapoolId = searchParams.get('datapoolId') ?? null
  const sourceParam = searchParams.get('source')

  const createDataset = useCreateDataset()
  const isLoading = createDataset.isPending

  const form = useForm<DatasetCreateFormData>({
    resolver: zodResolver(DatasetCreateFormSchema),
    defaultValues: {
      name: '',
      datapoolId: datapoolId,
      description: '',
    },
  })

  const { handleFormValidationError } = useError()

  const handleCreateDataset = async (formData: DatasetCreateFormData): Promise<boolean> => {
    try {
      const { data } = await createDataset.mutateAsync({
        name: formData.name,
        description: formData.description,
        ...(formData.datapoolId && { datapoolId: formData.datapoolId }),
      })
      toast.success(t('messages.createSuccess'))
      router.push(`/datasets/${data.id}?mode=edit`)
      return true
    } catch {
      toast.error(tCommon('errors.unexpectedError'))
    }
    return false
  }

  const handleSave = async (): Promise<boolean> => {
    let isSaved = false

    await form.handleSubmit(
      async formData => {
        isSaved = await handleCreateDataset(formData)
      },
      errors => {
        handleFormValidationError(errors)
        isSaved = false
      },
    )()

    return isSaved
  }

  useRegisterUnsavedChanges(form.formState.isDirty, handleSave)

  const handleCancel = () => {
    if (sourceParam === 'datapools') {
      router.push(`/datapools/${datapoolId}?subtab=datasets`)
      return
    }
    router.push(`/datasets?${searchParams.toString()}`)
  }

  const customElementCreateMode = (
    <div className="flex items-center gap-4 mr-3.5">
      <Button onClick={() => handleCancel()} type="button" variant="ghost">
        {tCommon('actions.cancel')}
      </Button>

      <Button type="submit" form="dataset-create-form" disabled={!form.formState.isDirty || isLoading}>
        {tCommon('actions.saveAndContinue')}
      </Button>
    </div>
  )

  return (
    <PageContainer testId="createDatasetPage" headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader title={t('create.title')} subtitle={t('create.subtitle')} customElement={customElementCreateMode} />
      <PageBackground className="overflow-y-auto" hasBackground>
        <ContentCard className={cn('overflow-auto')}>
          <Form {...form}>
            <form
              id="dataset-create-form"
              data-testid="datasetCreateForm"
              aria-label={`${tCommon('form')} ${t('create.title')}`}
              onSubmit={e => {
                e.preventDefault()
                void handleSave()
              }}
              className={cn('max-w-300 flex flex-col gap-2 pt-2')}
            >
              <DetailsFieldContainer className="pt-0 border-b-0">
                <SubHeader
                  title={t('create.basicInfo.title')}
                  titleClassName="text-2xl leading-none font-bold"
                  subtitle={tCommon('info.creationSubtitle')}
                />
              </DetailsFieldContainer>
              {isLoading ? (
                <LoadingSpinner className="h-[120px]" />
              ) : (
                <>
                  <DetailsFieldContainer className="max-w-300">
                    <TextField
                      id="datasetTitle"
                      form={form}
                      label={t('form.name')}
                      name="name"
                      placeholder={t('form.name')}
                      required
                    />
                  </DetailsFieldContainer>
                  <DetailsFieldContainer className="max-w-300">
                    <FormSelect
                      id="datasetDatapool"
                      placeholder={
                        canReadDatapools ? t('form.datapoolSelectPlaceholder') : tCommon('info.notAvailable')
                      }
                      label={t('form.datapoolSelect')}
                      options={datapoolOptions ?? []}
                      form={form}
                      name="datapoolId"
                      data-testid="datapoolSelect"
                      disabled={!canReadDatapools}
                      hasPlaceholderWhenDisabled
                    />
                  </DetailsFieldContainer>
                  <DetailsFieldContainer className="max-w-300" hasBorder={false}>
                    <FormTextArea
                      id="datasetDescription"
                      form={form}
                      label={t('form.description')}
                      name="description"
                      placeholder={t('form.description')}
                      hint={t('form.descriptionHint')}
                      maxLength={150}
                      hasCharacterCount
                      className="min-h-[100px] resize-none"
                    />
                  </DetailsFieldContainer>
                </>
              )}
            </form>
          </Form>
        </ContentCard>
      </PageBackground>
    </PageContainer>
  )
}
