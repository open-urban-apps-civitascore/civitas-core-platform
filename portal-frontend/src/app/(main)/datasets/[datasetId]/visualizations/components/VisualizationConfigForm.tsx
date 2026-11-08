'use client'

import { useTranslations } from 'next-intl'
import { UseFormReturn } from 'react-hook-form'

import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { VISUALIZATION_DESCRIPTION_MAX_LENGTH, VisualizationFormData } from '@/types/visualizations'

interface VisualizationConfigFormProps {
  form: UseFormReturn<VisualizationFormData>
  typeLabel: string
  isReadOnly?: boolean
}

export const VisualizationConfigForm = ({ form, typeLabel, isReadOnly = false }: VisualizationConfigFormProps) => {
  const t = useTranslations('datasets.overview.completion.visualizations.config')
  const isMobile = useIsMobile()

  const gridClass = isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]'
  const readOnlyValueClass = isMobile ? 'text-sm' : 'text-sm pl-3'
  const formItemProps = { className: gridClass }

  return (
    <div className="flex flex-col">
      <DetailsFieldContainer className="pt-0 pb-3">
        <SubHeader title={t('form.sectionTitle')} titleClassName="text-2xl leading-none font-bold" />
      </DetailsFieldContainer>

      <DetailsFieldContainer>
        <div className={cn(gridClass)}>
          <span className="text-sm leading-none font-medium">{t('form.type')}</span>
          <span className={readOnlyValueClass}>{typeLabel}</span>
        </div>
      </DetailsFieldContainer>

      <DetailsFieldContainer>
        <TextField
          id="visualizationTitle"
          form={form}
          label={t('form.title')}
          name="name"
          placeholder={t('form.titlePlaceholder')}
          required
          disabled={isReadOnly}
          formItemProps={formItemProps}
        />
      </DetailsFieldContainer>

      <DetailsFieldContainer className="border-b-0">
        <FormTextArea
          id="visualizationDescription"
          form={form}
          label={t('form.description')}
          name="description"
          placeholder={t('form.descriptionPlaceholder')}
          hint={t('form.descriptionHint')}
          maxLength={VISUALIZATION_DESCRIPTION_MAX_LENGTH}
          hasCharacterCount
          disabled={isReadOnly}
          className="min-h-[100px] resize-none"
          formItemProps={formItemProps}
        />
      </DetailsFieldContainer>
    </div>
  )
}
