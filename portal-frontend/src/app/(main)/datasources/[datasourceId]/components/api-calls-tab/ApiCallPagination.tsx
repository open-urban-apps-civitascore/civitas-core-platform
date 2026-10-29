import { ChevronDown } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { UseFormReturn, useWatch } from 'react-hook-form'

import { Checkbox } from '@/components/ui/checkbox'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { HTTP_PAGINATION_TYPES, HTTP_PARAMETER_LOCATIONS } from '@/const/connectors'
import {
  EMPTY_PAGINATIONS,
  getParameterNameCollisionPaths,
  HttpApiCallsForm,
  HttpCursorPaginationForm,
  HttpPageNumberPaginationForm,
  HttpPaginationType,
  HttpParameterLocation,
} from '@/types/connectors'

type PageNumberField = Exclude<keyof HttpPageNumberPaginationForm, 'isEnabled' | 'type'>
type CursorField = Exclude<keyof HttpCursorPaginationForm, 'isEnabled' | 'type' | 'cursorIn'>

interface PaginationFieldConfig<T> {
  name: T
  isRequired: boolean
  isNumeric: boolean
}

const PAGE_NUMBER_FIELDS: PaginationFieldConfig<PageNumberField>[] = [
  { name: 'pageParameter', isRequired: true, isNumeric: false },
  { name: 'pageSizeParameter', isRequired: true, isNumeric: false },
  { name: 'pageSize', isRequired: true, isNumeric: true },
  { name: 'startPage', isRequired: false, isNumeric: true },
  { name: 'endPage', isRequired: false, isNumeric: true },
]

const CURSOR_FIELDS: PaginationFieldConfig<CursorField>[] = [
  { name: 'cursorPath', isRequired: true, isNumeric: false },
  { name: 'cursorParameter', isRequired: true, isNumeric: false },
]

const LIMIT_FIELDS: PaginationFieldConfig<CursorField>[] = [
  { name: 'limitParameter', isRequired: false, isNumeric: false },
  { name: 'limit', isRequired: false, isNumeric: true },
]

const PAGINATION_TYPE_LABELS: Record<HttpPaginationType, string> = {
  [HTTP_PAGINATION_TYPES.PAGE_NUMBER]: 'type.pageNumber',
  [HTTP_PAGINATION_TYPES.CURSOR]: 'type.cursor',
}

const CURSOR_IN_LABELS: Record<HttpParameterLocation, string> = {
  [HTTP_PARAMETER_LOCATIONS.QUERY]: 'cursorIn.query',
  [HTTP_PARAMETER_LOCATIONS.HEADER]: 'cursorIn.header',
}

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
  const type = useWatch({ control: form.control, name: `${paginationPath}.type` })
  const isCursor = type === HTTP_PAGINATION_TYPES.CURSOR

  const revalidateParameterNameCollisions = () => void form.trigger([...getParameterNameCollisionPaths(index)])

  const handleChangeAndRevalidate = <T,>(value: T, onChange: (value: T) => void) => {
    onChange(value)
    revalidateParameterNameCollisions()
  }

  const handleTypeChange = (newType: HttpPaginationType) => {
    form.setValue(paginationPath, { ...EMPTY_PAGINATIONS[newType], isEnabled: true }, { shouldDirty: true })
    revalidateParameterNameCollisions()
  }
  const typeId = `${paginationPath}.type`

  const renderTextField = (config: PaginationFieldConfig<PageNumberField | CursorField>, isRevalidating: boolean) => {
    const { name, isRequired, isNumeric } = config
    return (
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
              <Input
                {...field}
                onChange={
                  isRevalidating
                    ? event => handleChangeAndRevalidate(event.target.value, field.onChange)
                    : field.onChange
                }
                inputMode={isNumeric ? 'numeric' : undefined}
                disabled={isDisabled}
              />
            </FormControl>
            <FormMessage className="col-start-2" />
          </FormItem>
        )}
      />
    )
  }

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
                onCheckedChange={isChecked => handleChangeAndRevalidate(isChecked === true, field.onChange)}
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
              <Select
                value={type}
                onValueChange={value => handleTypeChange(value as HttpPaginationType)}
                disabled={isDisabled}
              >
                <SelectTrigger id={typeId} className="w-full">
                  <SelectValue>{t(PAGINATION_TYPE_LABELS[type])}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  {Object.values(HTTP_PAGINATION_TYPES).map(paginationType => (
                    <SelectItem key={paginationType} value={paginationType}>
                      {t(PAGINATION_TYPE_LABELS[paginationType])}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {isCursor && <p className="col-start-2 text-sm text-muted-foreground">{t('cursorHint')}</p>}
            </div>

            {!isCursor && PAGE_NUMBER_FIELDS.map(config => renderTextField(config, false))}

            {isCursor && (
              <>
                {CURSOR_FIELDS.map(config => renderTextField(config, true))}
                <FormField
                  control={form.control}
                  name={`${paginationPath}.cursorIn`}
                  render={({ field }) => (
                    <FormItem className={ROW_CLASS_NAME}>
                      <FormLabel>
                        {t('cursorIn.label')}
                        <span className="text-red-500 ml-1">*</span>
                      </FormLabel>
                      <Select
                        value={field.value}
                        onValueChange={value =>
                          handleChangeAndRevalidate(value as HttpParameterLocation, field.onChange)
                        }
                        disabled={isDisabled}
                      >
                        <FormControl>
                          <SelectTrigger className="w-full">
                            <SelectValue>{t(CURSOR_IN_LABELS[field.value])}</SelectValue>
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {Object.values(HTTP_PARAMETER_LOCATIONS).map(location => (
                            <SelectItem key={location} value={location}>
                              {t(CURSOR_IN_LABELS[location])}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormMessage className="col-start-2" />
                    </FormItem>
                  )}
                />
                {LIMIT_FIELDS.map(config => renderTextField(config, true))}
              </>
            )}
          </CollapsibleContent>
        </Collapsible>
      </CollapsibleContent>
    </Collapsible>
  )
}
