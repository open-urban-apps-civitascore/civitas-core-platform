'use client'

import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { FormEvent } from 'react'

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
import { useRegisterUnsavedChanges } from '@/hooks/use-register-unsaved-changes'
import { cn } from '@/lib/utils'

import { useDatastructureCreation } from '../../[datastructureId]/hooks/useDatastructureCreation'

export const DatastructureCreateForm = () => {
  const t = useTranslations('datastructures')
  const tCommon = useTranslations('common')

  const router = useRouter()
  const searchParams = useSearchParams()

  const { form, isLoading, saveDatastructure } = useDatastructureCreation()

  const handleCreateDatastructure = async (): Promise<boolean> => {
    const response = await saveDatastructure()
    if (!response) return false

    router.push(`/datastructures/${response.id}?mode=edit`)
    return true
  }

  const handleSave = async (): Promise<boolean> => {
    let isSaved = false

    await form.handleSubmit(async () => {
      isSaved = await handleCreateDatastructure()
    })()

    return isSaved
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void handleSave()
  }

  const handleCancel = () => {
    router.push(`/datastructures?${searchParams.toString()}`)
  }

  useRegisterUnsavedChanges(form.formState.isDirty, handleSave)

  const customElementCreateMode = (
    <div className="flex items-center gap-4 mr-3.5">
      <Button onClick={() => handleCancel()} type="button" variant="ghost" disabled={isLoading}>
        {tCommon('actions.cancel')}
      </Button>

      <Button type="submit" form="datastructure-create-form" disabled={!form.formState.isDirty || isLoading}>
        {tCommon('actions.saveAndContinue')}
      </Button>
    </div>
  )

  return (
    <PageContainer testId="createDatastructurePage" headerType="withSubTabsOrSubtitle" className="overflow-hidden">
      <PageHeader title={t('create.title')} subtitle={t('create.subtitle')} customElement={customElementCreateMode} />
      <PageBackground className="overflow-y-auto" hasBackground>
        <ContentCard className={cn('overflow-auto')} footerElement={<FooterElement areAllFieldsRequired />}>
          <Form {...form}>
            <form
              id="datastructure-create-form"
              data-testid="datastructureCreateForm"
              aria-label={`${tCommon('form')} ${t('create.basicInfo.title')}`}
              onSubmit={handleSubmit}
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
                      id="datastructureName"
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
