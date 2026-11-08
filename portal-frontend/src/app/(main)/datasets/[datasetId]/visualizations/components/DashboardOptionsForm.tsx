'use client'

import { useTranslations } from 'next-intl'
import { UseFormReturn } from 'react-hook-form'

import { FormSelect } from '@/components/form/fields/FormSelect'
import { Checkbox } from '@/components/ui/checkbox'
import { FormControl, FormField, FormItem, FormLabel } from '@/components/ui/form'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { VisualizationFormData } from '@/types/visualizations'

interface DashboardOptionsFormProps {
  form: UseFormReturn<VisualizationFormData>
  isReadOnly: boolean
}

export const DashboardOptionsForm = ({ form, isReadOnly }: DashboardOptionsFormProps) => {
  const t = useTranslations('datasets.overview.completion.visualizations.config.options')

  const standaloneOptions = [0, 1, 2, 3].map(value => ({
    value: String(value),
    label: t(`standalone.values.${value}`),
  }))

  return (
    <div className="flex max-w-300 flex-col gap-8 py-4">
      <section className="flex flex-col gap-4">
        <div>
          <h2 className="text-lg font-semibold">{t('standalone.title')}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{t('standalone.description')}</p>
        </div>
        <FormField
          control={form.control}
          name="standaloneMode"
          render={({ field }) => (
            <FormItem className="grid grid-cols-[minmax(0,270px)_minmax(0,384px)] items-center gap-4">
              <FormLabel htmlFor="dashboardStandaloneMode">{t('standalone.label')}</FormLabel>
              <Select
                value={String(field.value)}
                onValueChange={value => field.onChange(Number(value))}
                disabled={isReadOnly}
              >
                <FormControl>
                  <SelectTrigger id="dashboardStandaloneMode" data-testid="dashboardStandaloneMode">
                    <SelectValue />
                  </SelectTrigger>
                </FormControl>
                <SelectContent>
                  {standaloneOptions.map(option => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FormItem>
          )}
        />
      </section>

      <section className="flex flex-col gap-4">
        <div>
          <h2 className="text-lg font-semibold">{t('filters.title')}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{t('filters.description')}</p>
        </div>
        <FormField
          control={form.control}
          name="showFilters"
          render={({ field }) => (
            <FormItem className="flex items-start gap-3">
              <FormControl>
                <Checkbox
                  id="dashboardShowFilters"
                  data-testid="dashboardShowFilters"
                  checked={field.value}
                  onCheckedChange={field.onChange}
                  disabled={isReadOnly}
                />
              </FormControl>
              <div className="flex flex-col gap-1">
                <FormLabel htmlFor="dashboardShowFilters">{t('filters.showLabel')}</FormLabel>
                <p className="text-sm text-muted-foreground">{t('filters.showDescription')}</p>
              </div>
            </FormItem>
          )}
        />
        <FormSelect
          id="dashboardFilterExpansion"
          label={t('filters.expansionLabel')}
          options={[
            { value: 'DEFAULT', label: t('filters.expansionValues.DEFAULT') },
            { value: 'COLLAPSED', label: t('filters.expansionValues.COLLAPSED') },
            { value: 'EXPANDED', label: t('filters.expansionValues.EXPANDED') },
          ]}
          form={form}
          name="filterExpansion"
          disabled={isReadOnly || !form.watch('showFilters')}
        />
      </section>
    </div>
  )
}
