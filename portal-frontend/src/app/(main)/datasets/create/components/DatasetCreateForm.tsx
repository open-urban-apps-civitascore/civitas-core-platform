'use client'
import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { useCreateDataset } from '@/app/services/api/datasets/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { TextField } from '@/components/form/fields/TextField'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { cn } from '@/lib/utils'
import { DatasetCreateFormData, DatasetCreateFormSchema } from '@/types/datasets'

export const DatasetCreateForm = () => {
  const t = useTranslations('datasets')
  const tCommon = useTranslations('common')

  const router = useRouter()
  const searchParams = useSearchParams()

  const createDataset = useCreateDataset()
  const isLoading = createDataset.isPending

  const form = useForm<DatasetCreateFormData>({
    resolver: zodResolver(DatasetCreateFormSchema),
    defaultValues: {
      name: '',
    },
  })

  const handleCreateDataset = async (formData: DatasetCreateFormData) => {
    createDataset.mutate(
      {
        name: formData.name,
      },
      {
        onSuccess: ({ data }) => {
          toast.success(t('messages.createSuccess'))
          router.push(`/datasets/${data.id}?mode=edit`)
        },
        onError: () => toast.error(tCommon('errors.unexpectedError')),
      },
    )
  }

  const handleCancel = () => {
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
        <ContentCard className={cn('h-full overflow-auto')}>
          <Form {...form}>
            <form
              id="dataset-create-form"
              data-testid="datasetCreateForm"
              aria-label={`${tCommon('form')} ${t('create.title')}`}
              onSubmit={form.handleSubmit(handleCreateDataset)}
              className={cn('max-w-300 flex flex-col gap-2 pt-2')}
            >
              <DetailsFieldContainer className="pt-0 border-b-0">
                <SubHeader
                  title={t('create.form.title')}
                  titleClassName="text-2xl leading-none font-bold"
                  subtitle={t('create.form.subtitle')}
                />
              </DetailsFieldContainer>
              {isLoading ? (
                <LoadingSpinner className="h-[120px]" />
              ) : (
                <DetailsFieldContainer className="max-w-300">
                  <TextField
                    id="datasetTitle"
                    form={form}
                    label={t('create.form.name')}
                    name="name"
                    placeholder={t('create.form.name')}
                    required
                  />
                </DetailsFieldContainer>
              )}
            </form>
          </Form>
        </ContentCard>
      </PageBackground>
    </PageContainer>
  )
}
