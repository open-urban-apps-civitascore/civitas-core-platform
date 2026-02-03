'use client'

import { useTranslations } from 'next-intl'
import { useMemo } from 'react'
import { UseFormReturn } from 'react-hook-form'

import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { TextField } from '@/components/form/fields/TextField'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { DatasourceFormInput } from '@/types/datasources'

import { TagsMultiSelect } from './TagsMultiSelect'

interface BasicInfoTabProps {
  form: UseFormReturn<DatasourceFormInput>
  isReadOnly?: boolean
  isDraftMode?: boolean
}

export const BasicInfoTab = (props: BasicInfoTabProps) => {
  const { form, isReadOnly = false, isDraftMode = false } = props
  const t = useTranslations('datasources')
  const isMobile = useIsMobile()

  const tagsWatch = form.watch('tags')
  const descriptionWatch = form.watch('description')

  const characterCount = useMemo(() => descriptionWatch?.length || 0, [descriptionWatch])

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
        <FormField
          control={form.control}
          name="description"
          render={({ field }) => (
            <FormItem className={cn(isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]')}>
              <div>
                <FormLabel className={isDraftMode ? 'data-[error=true]:text-foreground' : ''}>
                  {t('form.description')}
                  {!isDraftMode && <span className="text-red-500 ml-1">*</span>}
                </FormLabel>
                <p className="text-sm text-muted-foreground mt-1">{t('form.descriptionHint')}</p>
              </div>
              <div>
                <FormControl>
                  <Textarea
                    data-testid="descriptionTextArea"
                    placeholder={t('form.description')}
                    className={cn(
                      'min-h-[100px] resize-none disabled:opacity-100 disabled:text-muted-foreground disabled:border-hidden disabled:shadow-none',
                      isDraftMode &&
                        'aria-[invalid=true]:border-input aria-[invalid=true]:ring-ring/50 dark:aria-[invalid=true]:ring-ring/50',
                    )}
                    maxLength={150}
                    disabled={isReadOnly}
                    {...field}
                  />
                </FormControl>
                <div className="flex justify-between mt-1">
                  {!isDraftMode && <FormMessage data-testid="descriptionFormMessage" />}
                  <span className="text-sm text-muted-foreground">{characterCount}/150</span>
                </div>
              </div>
            </FormItem>
          )}
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
