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
import { DatastructureFormDraft, DatastructureVersionFormData } from '@/types/datastructures'

interface VersionInfoTabProps {
  form: UseFormReturn<DatastructureVersionFormData>
  isReadOnly?: boolean
}

export const VersionInfoTab = (props: VersionInfoTabProps) => {
  const { form, isReadOnly = false } = props
  const t = useTranslations('datastructureVersion')

  return (
    <ContentCard className={cn('h-full overflow-auto')} footerElement={<FooterElement />}>
      <div className="max-w-300 flex flex-col" data-testid="versionInfoTab">
        <DetailsFieldContainer className="pt-0 pb-4 ">
          <SubHeader title={t('versionInfo.title')} />
        </DetailsFieldContainer>

        <DetailsFieldContainer className="max-w-300">
          <TextField
            id="datastructureName"
            form={form}
            label={t('form.name')}
            name="versionNumber"
            placeholder={t('form.versionNumberPlaceholder')}
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
