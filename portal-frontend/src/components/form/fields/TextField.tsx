import { DetailedHTMLProps, HTMLAttributes } from 'react'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { InputPropsWithoutForm } from '@/types/common'

interface TextFieldProps<T extends FieldValues> extends InputPropsWithoutForm {
  form: UseFormReturn<T>
  name: Path<T>
  placeholder: string
  label: string
  // eslint-disable-next-line react/boolean-prop-naming
  required?: boolean
  formItemProps?: DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement>
  shouldShowErrors?: boolean
  manualError?: string
}

export const TextField = <T extends FieldValues>(props: TextFieldProps<T>) => {
  const {
    form,
    name,
    placeholder,
    label,
    required = false,
    formItemProps,
    disabled,
    shouldShowErrors = true,
    manualError,
    type,
  } = props
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
                type={type}
                autoComplete={type === 'password' ? 'new-password' : undefined}
                className="disabled:opacity-100 disabled:border-transparent disabled:shadow-none disabled:h-9 disabled:py-0"
                placeholder={disabled ? undefined : placeholder}
                {...field}
                disabled={disabled}
                aria-invalid={!!manualError || !!form.formState.errors[name]}
              />
            </FormControl>
            {shouldShowErrors && <FormMessage data-testid={`${name}FormMessage`} className="mt-2" />}
            {manualError && (
              <FormMessage data-testid={`${name}ManualFormMessage`} className="mt-2">
                {manualError}
              </FormMessage>
            )}
          </div>
        </FormItem>
      )}
    />
  )
}
