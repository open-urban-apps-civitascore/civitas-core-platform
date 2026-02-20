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
import { DatasourceFormDraft } from '@/types/datasources'

interface BasicInfoTabProps {
  form: UseFormReturn<DatasourceFormDraft>
  isReadOnly?: boolean
}

export const BasicInfoTab = (props: BasicInfoTabProps) => {
  const { form, isReadOnly = false } = props
  const t = useTranslations('datasources')
  const tCommon = useTranslations('common')

  return (
    <ContentCard className={cn('h-full overflow-auto')} footerElement={<FooterElement />}>
      <div className="max-w-300 flex flex-col gap-2 pt-2" data-testid="basicInfoTab">
        <DetailsFieldContainer className="pt-0 border-b-0">
          <SubHeader
            title={t('edit.basicInfo.title')}
            titleClassName="text-2xl leading-none font-bold"
            subtitle={t('edit.basicInfo.subtitle')}
          />
        </DetailsFieldContainer>

        <DetailsFieldContainer className="max-w-300">
          <TextField
            id="datasourceName"
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
            hint={tCommon('info.descriptionHint')}
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
