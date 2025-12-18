import { DetailedHTMLProps, HTMLAttributes, InputHTMLAttributes } from 'react'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'

interface TextFieldProps<T extends FieldValues> extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'form' | 'onChange'
> {
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
              <Input
                data-testid={`${name}TextField`}
                data-test-element="formField"
                className="disabled:opacity-100 disabled:text-muted-foreground disabled:border-hidden disabled:shadow-none disabled:h-4 disabled:py-0"
                placeholder={placeholder}
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
