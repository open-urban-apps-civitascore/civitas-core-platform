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
import { DatastructureVersionFormData } from '@/types/datastructures'

interface VersionInfoTabProps {
  form: UseFormReturn<DatastructureVersionFormData>
  isReadOnly?: boolean
}

export const VersionInfoTab = (props: VersionInfoTabProps) => {
  const { form, isReadOnly = false } = props
  const t = useTranslations('datastructureVersion')
  const tCommon = useTranslations('common')

  return (
    <ContentCard className={cn('h-full overflow-auto')} footerElement={<FooterElement />}>
      <div className="max-w-300 flex flex-col" data-testid="versionInfoTab">
        <DetailsFieldContainer className="pt-0 pb-4 ">
          <SubHeader title={t('versionInfo.title')} />
        </DetailsFieldContainer>

        <DetailsFieldContainer className="max-w-300">
          <TextField
            id="versionNumber"
            form={form}
            label={t('versionInfo.versionNumber')}
            name="versionNumber"
            placeholder={t('versionInfo.versionNumberPlaceholder')}
            disabled={isReadOnly}
            required
          />
        </DetailsFieldContainer>

        <DetailsFieldContainer className="max-w-300">
          <FormTextArea
            form={form}
            name="description"
            label={t('versionInfo.description')}
            placeholder={t('versionInfo.description')}
            hint={tCommon('info.descriptionHint')}
            maxLength={150}
            hasCharacterCount
            disabled={isReadOnly}
            required
            className="min-h-[100px] resize-none"
          />
        </DetailsFieldContainer>

        <DetailsFieldContainer className="max-w-300">
          <TextField
            id="source"
            form={form}
            label={t('versionInfo.source')}
            name="versionNumber"
            placeholder={t('source.OWN')}
            disabled={true}
            required
          />
        </DetailsFieldContainer>
      </div>
    </ContentCard>
  )
}
