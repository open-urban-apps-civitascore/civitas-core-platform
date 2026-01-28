'use client'

import { useTranslations } from 'next-intl'
import { UseFormReturn } from 'react-hook-form'

import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { TextArea } from '@/components/form/fields/TextArea'
import { TextField } from '@/components/form/fields/TextField'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { FormItem, FormLabel } from '@/components/ui/form'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { DatasourceFormData } from '@/types/datasources'

import { TagsMultiSelect } from './TagsMultiSelect'

interface BasicInfoTabProps {
  form: UseFormReturn<DatasourceFormData>
  isReadOnly?: boolean
  isDraftMode?: boolean
}

export const BasicInfoTab = (props: BasicInfoTabProps) => {
  const { form, isReadOnly = false, isDraftMode = false } = props
  const t = useTranslations('datasources')
  const isMobile = useIsMobile()

  const tagsWatch = form.watch('tags')

  const handleTagsChange = (tags: string[]) => {
    form.setValue('tags', tags, { shouldDirty: true })
  }

  return (
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
        <TextArea
          form={form}
          name="description"
          label={t('form.description')}
          placeholder={t('form.description')}
          hint={t('form.descriptionHint')}
          maxLength={150}
          hasCharacterCount
          disabled={isReadOnly}
          required={!isDraftMode}
          className="min-h-[100px] resize-none"
        />
      </DetailsFieldContainer>

      <DetailsFieldContainer className={cn('mb-6 max-w-300')}>
        <FormItem className={cn(isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]')}>
          <FormLabel>
            {t('form.tags')} <span className="text-muted-foreground">{t('form.tagsOptional')}</span>
          </FormLabel>
          <div data-testid="tagsField">
            <TagsMultiSelect selectedTags={tagsWatch} onTagsChange={handleTagsChange} isDisabled={isReadOnly} />
          </div>
        </FormItem>
      </DetailsFieldContainer>
    </div>
  )
}
