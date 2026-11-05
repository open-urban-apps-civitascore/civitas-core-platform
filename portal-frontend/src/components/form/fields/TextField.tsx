import { DetailedHTMLProps, HTMLAttributes, useMemo } from 'react'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { InputPropsWithoutForm } from '@/types/common'

import { DISABLED_INPUT_CLASS_NAME } from './disabledFieldClassNames'

interface TextFieldProps<T extends FieldValues> extends InputPropsWithoutForm {
  form: UseFormReturn<T>
  name: Path<T>
  placeholder?: string
  label: string
  // eslint-disable-next-line react/boolean-prop-naming
  required?: boolean
  formItemProps?: DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement>
  shouldShowErrors?: boolean
  manualError?: string
  maxLength?: number
  hasCharacterCount?: boolean
  normalize?: (value: string) => string
  onValueChange?: (value: string) => void
  inputClassName?: string
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
    maxLength,
    hasCharacterCount,
    autoComplete,
    inputMode,
    normalize,
    onValueChange,
    inputClassName,
  } = props
  const isMobile = useIsMobile()
  const fieldValue = form.watch(name)
  const characterCount = useMemo(() => (fieldValue as string)?.length || 0, [fieldValue])
  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field, fieldState }) => (
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
                inputMode={inputMode}
                autoComplete={autoComplete ?? (type === 'password' ? 'new-password' : undefined)}
                className={cn(DISABLED_INPUT_CLASS_NAME, inputClassName)}
                placeholder={disabled ? undefined : placeholder}
                maxLength={maxLength}
                {...field}
                onChange={event => {
                  field.onChange(event)
                  onValueChange?.(event.target.value)
                }}
                onBlur={() => {
                  if (normalize && typeof field.value === 'string') {
                    const normalized = normalize(field.value)
                    if (normalized !== field.value) field.onChange(normalized)
                  }
                  field.onBlur()
                }}
                disabled={disabled}
                aria-invalid={!!manualError || !!fieldState.error}
              />
            </FormControl>
            <div className="flex justify-between">
              <div>
                {shouldShowErrors && <FormMessage data-testid={`${name}FormMessage`} className="mt-2" />}
                {manualError && (
                  <FormMessage data-testid={`${name}ManualFormMessage`} className="mt-2">
                    {manualError}
                  </FormMessage>
                )}
              </div>
              {hasCharacterCount && maxLength && !disabled && (
                <span className="text-sm text-muted-foreground ml-auto mt-2">
                  {characterCount}/{maxLength}
                </span>
              )}
            </div>
          </div>
        </FormItem>
      )}
    />
  )
}
