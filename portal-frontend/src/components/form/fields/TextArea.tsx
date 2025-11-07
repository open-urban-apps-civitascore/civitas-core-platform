import React, { DetailedHTMLProps, HTMLAttributes, InputHTMLAttributes } from 'react'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import { useIsMobile } from '@/hooks/use-mobile'

interface TextAreaProps<T extends FieldValues>
  extends Omit<InputHTMLAttributes<HTMLTextAreaElement>, 'form' | 'onChange'> {
  form: UseFormReturn<T>
  name: Path<T>
  placeholder: string
  label: string
  formItemProps?: DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement>
}

export const TextArea = <T extends FieldValues>(props: TextAreaProps<T>) => {
  const { form, name, placeholder, label, required, disabled, formItemProps } = props
  const isMobile = useIsMobile()

  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem
          className={cn(
            isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]',
            formItemProps?.className,
          )}
        >
          <FormLabel>
            {label}
            {required && <span className="text-red-500 ml-1">*</span>}
          </FormLabel>
          <FormControl>
            <Textarea
              className="disabled:opacity-100 disabled:border-hidden disabled:shadow-none disabled:min-h-4  disabled:py-0 disabled:resize-none"
              placeholder={placeholder}
              {...field}
              disabled={disabled}
            />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  )
}
