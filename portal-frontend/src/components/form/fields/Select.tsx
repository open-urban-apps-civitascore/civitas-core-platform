import { SelectTriggerProps } from '@radix-ui/react-select'
import { Check } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Select as ShadcnSelect, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { SelectOption } from '@/types/common'

export interface AccessibleSelectProps<T extends FieldValues> {
  id: string
  label: string
  placeholder?: string
  options: SelectOption[]
  form: UseFormReturn<T>
  name: Path<T>
  // eslint-disable-next-line react/boolean-prop-naming
  required?: boolean
  className?: string
  // eslint-disable-next-line react/boolean-prop-naming
  disabled?: boolean
  selectTriggerProps?: SelectTriggerProps
  onChange?: (value: string) => void
}

export const Select = <T extends FieldValues>(props: AccessibleSelectProps<T>) => {
  const {
    id,
    label,
    placeholder,
    options,
    form,
    name,
    required = false,
    className,
    disabled = false,
    selectTriggerProps,
    onChange,
  } = props

  const t = useTranslations('common')
  const isMobile = useIsMobile()

  return (
    <FormField
      control={form.control}
      name={name}
      rules={{ required: required ? t('errors.required') : false }}
      render={({ field }) => {
        return (
          <FormItem
            className={cn(isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]', className)}
          >
            <FormLabel htmlFor={id} className="text-sm font-medium text-gray-700 dark:text-gray-200">
              {label}
              {required && <span className="text-red-500 ml-1">*</span>}
            </FormLabel>

            <ShadcnSelect value={field.value} onValueChange={onChange ?? field.onChange}>
              <SelectTrigger
                id={id}
                data-testid={`${name}SelectTrigger`}
                data-test-element="formField"
                aria-label={label}
                className={cn(
                  'w-full disabled:opacity-100 disabled:border-hidden disabled:shadow-none disabled:h-4 disabled:py-0 disabled:pointer-events-none',
                  selectTriggerProps?.className,
                )}
                style={{ height: disabled ? '20px' : '' }}
                disabled={disabled}
              >
                <SelectValue placeholder={placeholder} />
              </SelectTrigger>
              <SelectContent data-testid={`${name}SelectContent`}>
                <SelectItem data-testid={`${name}SelectItemPlaceholder`} value="none" disabled={required}>
                  {placeholder}
                </SelectItem>

                {options.map((option, index) => (
                  <SelectItem data-testid={`${name}SelectItem${index}`} key={option.value} value={option.value}>
                    <span>{option.label}</span>
                    <Check className="ml-auto h-4 w-4 opacity-0 group-data-[state=checked]:opacity-100" />
                  </SelectItem>
                ))}
              </SelectContent>
            </ShadcnSelect>
            <FormMessage />
          </FormItem>
        )
      }}
    />
  )
}
