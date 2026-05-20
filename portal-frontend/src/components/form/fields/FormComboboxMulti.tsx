import { useTranslations } from 'next-intl'
import { DetailedHTMLProps, HTMLAttributes } from 'react'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { ComboboxMultiSelect } from '@/components/combobox-multi-select/ComboboxMultiSelect'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'

interface FormComboboxMultiProps<T extends FieldValues> {
  id: string
  label: string
  items: string[]
  form: UseFormReturn<T>
  name: Path<T>
  // eslint-disable-next-line react/boolean-prop-naming
  required?: boolean
  // eslint-disable-next-line react/boolean-prop-naming
  disabled?: boolean
  hasSelectAllOption?: boolean
  shouldShowErrors?: boolean
  manualError?: string
  formItemProps?: DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement>
}

export const FormComboboxMulti = <T extends FieldValues>(props: FormComboboxMultiProps<T>) => {
  const {
    id,
    label,
    items,
    form,
    name,
    required = false,
    disabled = false,
    hasSelectAllOption,
    shouldShowErrors = true,
    manualError,
    formItemProps,
  } = props

  const t = useTranslations('common')
  const isMobile = useIsMobile()

  return (
    <FormField
      control={form.control}
      name={name}
      rules={{ required: required ? t('errors.required') : false }}
      render={({ field, fieldState }) => (
        <FormItem
          className={cn(
            isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]',
            formItemProps?.className,
          )}
        >
          <FormLabel htmlFor={id}>
            {label}
            {required && <span className="text-red-500 ml-1">*</span>}
          </FormLabel>
          <div>
            <FormControl>
              <ComboboxMultiSelect
                items={items}
                hasSelectAllOption={hasSelectAllOption}
                onValueChange={field.onChange}
                disabled={disabled}
                isInvalid={fieldState.invalid}
                testId={`${String(name)}Combobox`}
              />
            </FormControl>
            {shouldShowErrors && <FormMessage data-testid={`${String(name)}FormMessage`} className="mt-2" />}
            {manualError && (
              <FormMessage data-testid={`${String(name)}ManualFormMessage`} className="mt-2">
                {manualError}
              </FormMessage>
            )}
          </div>
        </FormItem>
      )}
    />
  )
}
