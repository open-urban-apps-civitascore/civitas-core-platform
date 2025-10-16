import { SelectTriggerProps } from '@radix-ui/react-select'
import { Check } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { FormField, FormMessage } from '@/components/ui/form'
import { Select as ShadcnSelect, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'

export type SelectOption = {
  value: string
  label: string
}

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

  return (
    <FormField
      control={form.control}
      name={name}
      rules={{ required: required ? t('errors.required') : false }}
      render={({ field }) => {
        return (
          <div className={className}>
            <label htmlFor={id} className="text-sm font-medium text-gray-700 dark:text-gray-200">
              {label}
              {required && <span className="text-red-500 ml-1">*</span>}
            </label>

            <ShadcnSelect value={field.value.id} onValueChange={onChange ?? field.onChange}>
              <SelectTrigger
                id={id}
                aria-label={label}
                className={cn('w-full', selectTriggerProps?.className)}
                disabled={disabled}
              >
                <SelectValue placeholder={placeholder} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="none" disabled={required}>
                  {placeholder}
                </SelectItem>

                {options.map(option => (
                  <SelectItem key={option.value} value={option.value}>
                    <span>{option.label}</span>
                    <Check className="ml-auto h-4 w-4 opacity-0 group-data-[state=checked]:opacity-100" />
                  </SelectItem>
                ))}
              </SelectContent>
            </ShadcnSelect>
            <FormMessage />
          </div>
        )
      }}
    />
  )
}
