'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useForm } from 'react-hook-form'

import { useCreateDatasource } from '@/app/services/api/datasources/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { TextField } from '@/components/form/fields/TextField'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { CONNECTION_TYPES } from '@/const/connectors'
import { cn } from '@/lib/utils'
import { STATUS_TYPES } from '@/types/common'
import { DatasourceCreateFormData, DatasourceCreateFormSchema } from '@/types/datasources'

export const DatasourceCreateForm = () => {
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')

  const router = useRouter()
  const searchParams = useSearchParams()

  const createDatasource = useCreateDatasource()
  const isLoading = createDatasource.isPending

  const form = useForm<DatasourceCreateFormData>({
    resolver: zodResolver(DatasourceCreateFormSchema),
    defaultValues: {
      name: '',
    },
  })

  const handleCreateDatasource = async (formData: DatasourceCreateFormData) => {
    createDatasource.mutate(
      {
        name: formData.name,
        description: '',
        status: STATUS_TYPES.DRAFT,
        connector: null,
        connection: CONNECTION_TYPES.INACTIVE,
        lastActive: new Date().toISOString(),
      },
      {
        onSuccess: ({ data }) => {
          router.push(`/datasources/${data.id}`)
        },
      },
    )
  }

  const handleCancel = () => {
    router.push(`/datasources?${searchParams.toString()}`)
  }

  return (
    <PageContainer testId="createDatasourcePage" headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <div className="w-full flex flex-col h-[var(--title-height)] py-[var(--layout-padding)] border-b-1">
        <div className="flex items-center justify-between px-[var(--layout-padding)]">
          <div className="flex-1 min-w-0">
            <h1 className="text-3xl font-bold truncate max-w-full min-w-0">{t('create.title')}</h1>
            <p className="mt-2 text-muted-foreground">{t('create.subtitle')}</p>
          </div>
          <div className="flex items-center gap-4 flex-shrink-0">
            <Button
              data-testid="cancelButton"
              type="button"
              variant="ghost"
              onClick={handleCancel}
              disabled={isLoading}
            >
              {tCommon('actions.cancel')}
            </Button>
            <Button
              data-testid="submitButton"
              type="button"
              onClick={form.handleSubmit(handleCreateDatasource)}
              disabled={!form.formState.isDirty || isLoading}
            >
              {tCommon('actions.saveAndContinue')}
            </Button>
          </div>
        </div>
      </div>
      <PageBackground className="overflow-y-auto">
        <ContentCard className={cn('h-full overflow-auto')}>
          <Form {...form}>
            <form
              data-testid="datasourceCreateForm"
              aria-label={`${tCommon('form')} ${t('create.basicInfo.title')}`}
              onSubmit={form.handleSubmit(handleCreateDatasource)}
              className={cn('max-w-300 flex flex-col gap-2 pt-2')}
            >
              <DetailsFieldContainer className="pt-0 border-b-0">
                <SubHeader
                  title={t('create.basicInfo.title')}
                  titleClassName="text-2xl leading-none font-bold"
                  subtitle={t('create.basicInfo.subtitle')}
                />
              </DetailsFieldContainer>
              {isLoading ? (
                <LoadingSpinner className="h-[120px]" />
              ) : (
                <DetailsFieldContainer className="max-w-300">
                  <TextField
                    id="datasourceName"
                    form={form}
                    label={t('form.name')}
                    name="name"
                    placeholder={t('form.namePlaceholder')}
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
