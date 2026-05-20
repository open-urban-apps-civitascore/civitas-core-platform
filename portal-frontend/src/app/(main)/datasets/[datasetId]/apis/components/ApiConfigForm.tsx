'use client'

import { useTranslations } from 'next-intl'
import { FocusEvent } from 'react'
import { UseFormReturn } from 'react-hook-form'

import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import {
  API_TYPE_QUERY,
  ApiTypeQuery,
  NAMED_API_DESCRIPTION_MAX_LENGTH,
  NamedApiFormData,
  PERSISTENCE_OPTIONS,
  SLUG_MAX_LENGTH,
} from '@/types/namedApis'

interface ApiConfigFormProps {
  form: UseFormReturn<NamedApiFormData>
  apiType: ApiTypeQuery
  isReadOnly: boolean
  datasetId: string
  typeLabel: string
  urlPreviewSlug: string
  onSlugBlur: (event: FocusEvent<HTMLInputElement>) => void
}

export const ApiConfigForm = (props: ApiConfigFormProps) => {
  const { form, apiType, isReadOnly, datasetId, typeLabel, urlPreviewSlug, onSlugBlur } = props
  const t = useTranslations('datasets.overview.completion.apis.config')
  const isMobile = useIsMobile()

  const isWfsWms = apiType === API_TYPE_QUERY.WFS_WMS
  const persistenceOptions = isWfsWms ? [PERSISTENCE_OPTIONS.POSTGIS] : [PERSISTENCE_OPTIONS.FROST]

  // Wider right column than the shared default so long URL previews don't wrap.
  const gridClass = isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,540px)]'
  // Read-only values sit at the column edge while inputs have inner px-3 padding;
  // add pl-3 so plain text aligns vertically with the input text.
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
          <span data-testid="apiTypeReadOnly" className={readOnlyValueClass}>
            {typeLabel}
          </span>
        </div>
      </DetailsFieldContainer>

      <DetailsFieldContainer>
        {isWfsWms ? (
          <FormSelect
            id="apiPersistence"
            label={t('form.persistence')}
            options={persistenceOptions.map(o => ({ value: o.value, label: o.label }))}
            form={form}
            name="persistence"
            disabled={isReadOnly}
            required
            formItemProps={formItemProps}
          />
        ) : (
          <div className={cn(gridClass)}>
            <div>
              <span className="text-sm leading-none font-medium">{t('form.persistence')}</span>
              <p className="text-sm text-muted-foreground mt-1">{t('form.persistenceHint')}</p>
            </div>
            <span data-testid="apiPersistenceReadOnly" className={readOnlyValueClass}>
              {PERSISTENCE_OPTIONS.FROST.label}
            </span>
          </div>
        )}
      </DetailsFieldContainer>

      <DetailsFieldContainer>
        <FormField
          control={form.control}
          name="slug"
          render={({ field, fieldState }) => (
            <FormItem className={cn(gridClass)}>
              <FormLabel htmlFor="apiSlug">
                {t('form.slug')}
                <span className="text-red-500 ml-1">*</span>
              </FormLabel>
              <div>
                <FormControl>
                  <Input
                    id="apiSlug"
                    data-testid="slugTextField"
                    data-test-element="formField"
                    className="disabled:opacity-100 disabled:border-transparent disabled:shadow-none disabled:h-9 disabled:py-0"
                    {...field}
                    onBlur={event => {
                      field.onBlur()
                      onSlugBlur(event)
                    }}
                    maxLength={SLUG_MAX_LENGTH}
                    disabled={isReadOnly}
                    aria-invalid={fieldState.isTouched && !!form.formState.errors.slug}
                  />
                </FormControl>
                {fieldState.isTouched && <FormMessage data-testid="slugFormMessage" className="mt-2" />}
              </div>
            </FormItem>
          )}
        />
      </DetailsFieldContainer>

      <DetailsFieldContainer>
        <div className={cn(gridClass)}>
          <span className="text-sm leading-none font-medium">{t('form.urlPreview')}</span>
          <span data-testid="apiUrlPreview" className={cn(readOnlyValueClass, 'break-all')}>
            {`/datasets/${datasetId}/`}
            <strong>{urlPreviewSlug}</strong>
          </span>
        </div>
      </DetailsFieldContainer>

      <DetailsFieldContainer>
        <TextField
          id="apiName"
          form={form}
          label={t('form.name')}
          name="name"
          placeholder={t('form.name')}
          disabled={isReadOnly}
          required
          formItemProps={formItemProps}
        />
      </DetailsFieldContainer>

      <DetailsFieldContainer className="border-b-0">
        <FormTextArea
          id="apiDescription"
          form={form}
          label={t('form.description')}
          name="description"
          placeholder={t('form.descriptionPlaceholder')}
          disabled={isReadOnly}
          hint={t('form.descriptionHint')}
          maxLength={NAMED_API_DESCRIPTION_MAX_LENGTH}
          hasCharacterCount
          className="min-h-[100px] resize-none"
          formItemProps={formItemProps}
        />
      </DetailsFieldContainer>
    </div>
  )
}
