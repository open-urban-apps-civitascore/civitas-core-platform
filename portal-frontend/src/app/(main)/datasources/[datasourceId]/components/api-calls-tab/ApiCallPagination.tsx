import { ChevronDown } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { ReactElement } from 'react'
import { UseFormReturn, useWatch } from 'react-hook-form'

import { DISABLED_CHECKBOX_CLASS_NAME } from '@/components/form/fields/disabledFieldClassNames'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { TextField } from '@/components/form/fields/TextField'
import { Checkbox } from '@/components/ui/checkbox'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { FormField } from '@/components/ui/form'
import { Label } from '@/components/ui/label'
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

  const handleCursorInChange = (value: string) => {
    form.setValue(`${paginationPath}.cursorIn`, value as HttpParameterLocation, { shouldDirty: true })
    revalidateParameterNameCollisions()
  }

  const typeId = `${paginationPath}.type` as const
  const paginationTypeOptions = Object.values(HTTP_PAGINATION_TYPES).map(paginationType => ({
    value: paginationType,
    label: t(PAGINATION_TYPE_LABELS[paginationType]),
  }))
  const cursorInOptions = Object.values(HTTP_PARAMETER_LOCATIONS).map(location => ({
    value: location,
    label: t(CURSOR_IN_LABELS[location]),
  }))

  const renderTextField = (config: PaginationFieldConfig<PageNumberField | CursorField>) => {
    const { name, isRequired, isNumeric } = config
    return (
      <TextField
        key={name}
        form={form}
        name={`${paginationPath}.${name}`}
        label={t(`${name}.label`)}
        required={isRequired}
        inputMode={isNumeric ? 'numeric' : undefined}
        disabled={isDisabled}
        formItemProps={{ className: ROW_CLASS_NAME }}
        onValueChange={revalidateParameterNameCollisions}
      />
    )
  }

  const renderPaginationFields = (): ReactElement => {
    switch (type) {
      case HTTP_PAGINATION_TYPES.PAGE_NUMBER:
        return <>{PAGE_NUMBER_FIELDS.map(renderTextField)}</>
      case HTTP_PAGINATION_TYPES.CURSOR:
        return (
          <>
            {CURSOR_FIELDS.map(renderTextField)}
            <FormSelect
              id={`${paginationPath}.cursorIn`}
              form={form}
              name={`${paginationPath}.cursorIn`}
              label={t('cursorIn.label')}
              options={cursorInOptions}
              required
              disabled={isDisabled}
              formItemProps={{ className: ROW_CLASS_NAME }}
              onChange={handleCursorInChange}
            />
            {LIMIT_FIELDS.map(renderTextField)}
          </>
        )
    }
  }

  return (
    <Collapsible open={isOpen} onOpenChange={onOpenChange} className="group/pagination pt-2">
      <CollapsibleTrigger asChild>
        <button type="button" className="flex w-full items-center justify-between text-left">
          <span className="text-sm font-semibold uppercase tracking-wide text-primary">{t('title')}</span>
          <ChevronDown className="size-4 group-data-[state=open]/pagination:rotate-180" />
        </button>
      </CollapsibleTrigger>

      <CollapsibleContent className="flex flex-col pt-6">
        <FormField
          control={form.control}
          name={`${paginationPath}.isEnabled`}
          render={({ field }) => (
            <Label className="w-fit">
              <Checkbox
                className={DISABLED_CHECKBOX_CLASS_NAME}
                checked={field.value}
                onCheckedChange={isChecked => handleChangeAndRevalidate(isChecked === true, field.onChange)}
                disabled={isDisabled}
              />
              {t('enable')}
            </Label>
          )}
        />

        <Collapsible open={isEnabled}>
          <CollapsibleContent className="flex flex-col gap-4 pt-6">
            <div className={ROW_CLASS_NAME}>
              <FormSelect
                id={typeId}
                form={form}
                name={typeId}
                label={t('type.label')}
                options={paginationTypeOptions}
                disabled={isDisabled}
                formItemProps={{ className: 'contents' }}
                onChange={value => handleTypeChange(value as HttpPaginationType)}
              />
              {isCursor && <p className="col-start-2 text-sm text-muted-foreground">{t('cursorHint')}</p>}
            </div>

            {renderPaginationFields()}
          </CollapsibleContent>
        </Collapsible>
      </CollapsibleContent>
    </Collapsible>
  )
}
