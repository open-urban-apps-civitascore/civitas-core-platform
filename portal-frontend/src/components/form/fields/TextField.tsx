import { DetailedHTMLProps, HTMLAttributes, InputHTMLAttributes } from 'react'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

interface TextFieldProps<T extends FieldValues>
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'form' | 'onChange'> {
  form: UseFormReturn<T>
  name: Path<T>
  placeholder: string
  label: string
  // eslint-disable-next-line react/boolean-prop-naming
  required?: boolean
  formItemProps?: DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement>
}

export const TextField = <T extends FieldValues>(props: TextFieldProps<T>) => {
  const { form, name, placeholder, label, required = false, formItemProps, disabled } = props
  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem className={cn(' gap-y-0', formItemProps?.className)}>
          <FormLabel>
            {label}
            {required && <span className="text-red-500 ml-1">*</span>}
          </FormLabel>
          <FormControl>
            <Input placeholder={placeholder} {...field} disabled={disabled} />
          </FormControl>
          <FormMessage className="mt-2" />
        </FormItem>
      )}
    />
  )
}
