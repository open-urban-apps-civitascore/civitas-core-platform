import { ChangeEvent, EventHandler, InputHTMLAttributes } from 'react'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'

interface TextFieldProps<T extends FieldValues> extends Omit<InputHTMLAttributes<HTMLInputElement>, 'form' | 'onChange'> {
  form: UseFormReturn<T>
  name: Path<T>
  placeholder: string
  label: string
}

export const TextField = <T extends FieldValues>(props: TextFieldProps<T>) => {
  const { form, name, placeholder, label, disabled = false } = props
  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Input placeholder={placeholder} {...field} />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  )
}
