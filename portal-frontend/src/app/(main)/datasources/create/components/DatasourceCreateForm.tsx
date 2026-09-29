'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { useCreateDatasource } from '@/app/services/api/datasources/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { FooterElement } from '@/components/form/FooterElement'
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
import { DatasourceCreateData, DatasourceCreateFormSchema } from '@/types/datasources'

export const DatasourceCreateForm = () => {
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')

  const router = useRouter()
  const searchParams = useSearchParams()
  const { handleFormValidationError } = useError()

  const createDatasource = useCreateDatasource()
  const isLoading = createDatasource.isPending

  const form = useForm<DatasourceCreateData>({
    resolver: zodResolver(DatasourceCreateFormSchema),
    defaultValues: {
      name: '',
      description: '',
    },
  })

  const handleCreateDatasource = async (formData: DatasourceCreateData): Promise<boolean> => {
    try {
      const { data } = await createDatasource.mutateAsync({ name: formData.name, description: formData.description })
      toast.success(tCommon('messages.createSuccess', { item: tCommon('items.datasource') }))
      router.push(`/datasources/${data.id}?mode=edit`)
      return true
    } catch {
      toast.error(t('errors.creationError'))
      return false
    }
  }

  const handleSave = async () => {
    let isSaved = false

    await form.handleSubmit(
      async formData => {
        isSaved = await handleCreateDatasource(formData)
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
    router.push(`/datasources?${searchParams.toString()}`)
  }

  const customElementCreateMode = (
    <div className="flex items-center gap-4 mr-3.5">
      <Button onClick={() => handleCancel()} type="button" variant="ghost" disabled={isLoading}>
        {tCommon('actions.cancel')}
      </Button>

      <Button type="submit" form="datasource-create-form" disabled={!form.formState.isDirty || isLoading}>
        {tCommon('actions.saveAndContinue')}
      </Button>
    </div>
  )

  return (
    <PageContainer testId="createDatasourcePage" headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader title={t('create.title')} subtitle={t('create.subtitle')} customElement={customElementCreateMode} />
      <PageBackground className="overflow-y-auto" hasBackground>
        <ContentCard className={cn('overflow-auto')} footerElement={<FooterElement areAllFieldsRequired />}>
          <Form {...form}>
            <form
              id="datasource-create-form"
              data-testid="datasourceCreateForm"
              aria-label={`${tCommon('form')} ${t('create.basicInfo.title')}`}
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
                      id="datasourceName"
                      form={form}
                      label={t('form.name')}
                      name="name"
                      placeholder={t('form.namePlaceholder')}
                      required
                    />
                  </DetailsFieldContainer>
                  <DetailsFieldContainer className="max-w-300" hasBorder={false}>
                    <FormTextArea
                      form={form}
                      name="description"
                      label={t('form.description')}
                      placeholder={t('form.description')}
                      hint={tCommon('info.descriptionHint')}
                      maxLength={150}
                      hasCharacterCount
                      className="min-h-[100px] resize-none"
                      required
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
