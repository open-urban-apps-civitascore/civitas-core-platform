import { ChangeEvent, EventHandler, InputHTMLAttributes } from 'react'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'

interface TextFieldProps<T extends FieldValues>
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'form' | 'onChange'> {
  form: UseFormReturn<T>
  name: Path<T>
  placeholder: string
  label: string
  required?: boolean
}

export const TextField = <T extends FieldValues>(props: TextFieldProps<T>) => {
  const { form, name, placeholder, label, required = false, disabled } = props
  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>
            {label}
            {required && <span className="text-red-500 ml-1">*</span>}
          </FormLabel>
          <FormControl>
            <Input placeholder={placeholder} {...field} disabled={disabled} />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  )
}
