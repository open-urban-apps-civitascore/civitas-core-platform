import React, { DetailedHTMLProps, HTMLAttributes, InputHTMLAttributes } from 'react'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'

interface TextAreaProps<T extends FieldValues>
  extends Omit<InputHTMLAttributes<HTMLTextAreaElement>, 'form' | 'onChange'> {
  form: UseFormReturn<T>
  name: Path<T>
  placeholder: string
  label: string
  formItemProps?: DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement>
}

export const TextArea = <T extends FieldValues>(props: TextAreaProps<T>) => {
  const { form, name, placeholder, label, required, formItemProps } = props

  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem className={formItemProps?.className}>
          <FormLabel>
            {label}
            {required && <span className="text-red-500 ml-1">*</span>}
          </FormLabel>
          <FormControl>
            <Textarea placeholder={placeholder} {...field} />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  )
}
