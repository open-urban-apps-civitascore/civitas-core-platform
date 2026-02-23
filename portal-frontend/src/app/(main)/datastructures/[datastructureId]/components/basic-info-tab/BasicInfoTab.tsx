'use client'

import { useTranslations } from 'next-intl'
import { UseFormReturn } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { FooterElement } from '@/components/form/FooterElement'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { cn } from '@/lib/utils'
import { DatastructureFormDraft } from '@/types/datastructures'

interface BasicInfoTabProps {
  form: UseFormReturn<DatastructureFormDraft>
  isReadOnly?: boolean
}

export const BasicInfoTab = (props: BasicInfoTabProps) => {
  const { form, isReadOnly = false } = props
  const t = useTranslations('datastructures')

  return (
    <ContentCard className={cn('h-full overflow-auto')} footerElement={<FooterElement />}>
      <div className="max-w-300 flex flex-col" data-testid="basicInfoTab">
        <DetailsFieldContainer className="pt-0 pb-4 ">
          <SubHeader title={t('edit.basicInfo.title')} subtitle={t('edit.basicInfo.subtitle')} />
        </DetailsFieldContainer>

        <DetailsFieldContainer className="max-w-300">
          <TextField
            id="datastructureName"
            form={form}
            label={t('form.name')}
            name="name"
            placeholder={t('form.namePlaceholder')}
            disabled={isReadOnly}
            required
          />
        </DetailsFieldContainer>

        <DetailsFieldContainer className="max-w-300">
          <FormTextArea
            form={form}
            name="description"
            label={t('form.description')}
            placeholder={t('form.description')}
            hint={t('form.descriptionHint')}
            maxLength={150}
            hasCharacterCount
            disabled={isReadOnly}
            required
            className="min-h-[100px] resize-none"
          />
        </DetailsFieldContainer>
      </div>
    </ContentCard>
  )
}
