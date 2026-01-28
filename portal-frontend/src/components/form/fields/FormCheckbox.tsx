/* eslint-disable react/boolean-prop-naming */
import { DetailedHTMLProps, HTMLAttributes } from 'react'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { Checkbox } from '@/components/ui/checkbox'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'

interface FormCheckboxProps<T extends FieldValues> {
  form: UseFormReturn<T>
  name: Path<T>
  label: string
  required?: boolean
  formItemProps?: DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement>
  disabled?: boolean
}

export const FormCheckbox = <T extends FieldValues>(props: FormCheckboxProps<T>) => {
  const { form, name, label, required = false, formItemProps, disabled } = props
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
          <div>
            <FormControl>
              <Checkbox
                data-testid={`${name}Checkbox`}
                data-test-element="formField"
                className="disabled:opacity-100 disabled:text-muted-foreground disabled:border-hidden disabled:shadow-none disabled:h-4 disabled:py-0"
                {...field}
                disabled={disabled}
              />
            </FormControl>
            <FormMessage data-testid={`${name}FormMessage`} className="mt-2" />
          </div>
        </FormItem>
      )}
    />
  )
}
