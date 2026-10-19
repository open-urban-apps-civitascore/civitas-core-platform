import { ChevronDown } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { UseFormReturn, useWatch } from 'react-hook-form'

import { Checkbox } from '@/components/ui/checkbox'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { HTTP_PAGINATION_TYPES } from '@/const/connectors'
import { HttpApiCallsForm, HttpPaginationForm } from '@/types/connectors'

type PaginationField = Exclude<keyof HttpPaginationForm, 'isEnabled' | 'type'>

const PAGINATION_FIELDS: { name: PaginationField; isRequired: boolean; isNumeric: boolean }[] = [
  { name: 'pageParameter', isRequired: true, isNumeric: false },
  { name: 'pageSizeParameter', isRequired: true, isNumeric: false },
  { name: 'pageSize', isRequired: true, isNumeric: true },
  { name: 'startPage', isRequired: false, isNumeric: true },
  { name: 'endPage', isRequired: false, isNumeric: true },
]

const ROW_CLASS_NAME = 'grid grid-cols-[220px_1fr] xl:grid-cols-[220px_calc(50%_-_220px)] items-center gap-x-4 gap-y-2'

interface ApiCallPaginationProps {
  form: UseFormReturn<HttpApiCallsForm>
  index: number
  isOpen: boolean
  isDisabled: boolean
  onOpenChange: (isOpen: boolean) => void
}

export const ApiCallPagination = (props: ApiCallPaginationProps) => {
  const { form, index, isOpen, isDisabled, onOpenChange } = props
  const t = useTranslations('datasources.apiCallsTab.pagination')

  const paginationPath = `configuration.apiCalls.${index}.pagination` as const
  const isEnabled = useWatch({ control: form.control, name: `${paginationPath}.isEnabled` })

  const handleEnabledChange = (isChecked: boolean, onChange: (isChecked: boolean) => void) => {
    onChange(isChecked)
    void form.trigger(paginationPath)
  }
  const typeId = `${paginationPath}.type`

  return (
    <Collapsible open={isOpen} onOpenChange={onOpenChange} className="group/pagination">
      <CollapsibleTrigger asChild>
        <button type="button" className="flex w-full items-center justify-between text-left">
          <span className="text-sm font-semibold uppercase tracking-wide text-primary">{t('title')}</span>
          <ChevronDown className="size-4 group-data-[state=open]/pagination:rotate-180" />
        </button>
      </CollapsibleTrigger>

      <CollapsibleContent className="flex flex-col pt-4">
        <FormField
          control={form.control}
          name={`${paginationPath}.isEnabled`}
          render={({ field }) => (
            <Label className="w-fit">
              <Checkbox
                checked={field.value}
                onCheckedChange={isChecked => handleEnabledChange(isChecked === true, field.onChange)}
                disabled={isDisabled}
              />
              {t('enable')}
            </Label>
          )}
        />

        <Collapsible open={isEnabled}>
          <CollapsibleContent className="flex flex-col gap-4 pt-4">
            <div className={ROW_CLASS_NAME}>
              <Label htmlFor={typeId}>{t('type.label')}</Label>
              <Select value={HTTP_PAGINATION_TYPES.PAGE_NUMBER} disabled>
                <SelectTrigger id={typeId} className="w-full">
                  <SelectValue>{t('type.pageNumber')}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={HTTP_PAGINATION_TYPES.PAGE_NUMBER}>{t('type.pageNumber')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {PAGINATION_FIELDS.map(({ name, isRequired, isNumeric }) => (
              <FormField
                key={name}
                control={form.control}
                name={`${paginationPath}.${name}`}
                render={({ field }) => (
                  <FormItem className={ROW_CLASS_NAME}>
                    <FormLabel>
                      {t(`${name}.label`)}
                      {isRequired && <span className="text-red-500 ml-1">*</span>}
                    </FormLabel>
                    <FormControl>
                      <Input {...field} inputMode={isNumeric ? 'numeric' : undefined} disabled={isDisabled} />
                    </FormControl>
                    <FormMessage className="col-start-2" />
                  </FormItem>
                )}
              />
            ))}
          </CollapsibleContent>
        </Collapsible>
      </CollapsibleContent>
    </Collapsible>
  )
}
