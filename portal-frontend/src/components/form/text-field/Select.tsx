import { Check } from 'lucide-react'

import { SelectContent, SelectItem, SelectTrigger, SelectValue, Select as ShadcnSelect } from '@/components/ui/select'
import { Controller, FieldValues, Path, UseFormReturn } from 'react-hook-form'

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
  required?: boolean
}

export const Select = <T extends FieldValues>(props: AccessibleSelectProps<T>) => {
  const { id, label, placeholder = 'Please select...', options, form, name, required = false } = props

  return (
    <Controller
      control={form.control}
      name={name}
      render={({ field }) => (
        <div>
          <label htmlFor={id} className="text-sm font-medium text-gray-700 dark:text-gray-200">
            {label}
            {required && <span className="text-red-500 ml-1">*</span>}
          </label>

          <ShadcnSelect value={field.value} onValueChange={field.onChange}>
            <SelectTrigger id={id} aria-label={label} className="w-[200px]">
              <SelectValue placeholder={placeholder} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="none" disabled>
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
        </div>
      )}
    />
  )
}
