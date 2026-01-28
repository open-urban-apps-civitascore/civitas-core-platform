import { DetailedHTMLProps, HTMLAttributes, useMemo } from 'react'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { InputPropsWithoutForm } from '@/types/common'

interface TextAreaProps<T extends FieldValues> extends InputPropsWithoutForm {
  form: UseFormReturn<T>
  name: Path<T>
  placeholder: string
  label: string
  formItemProps?: DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement>
  hint?: string
  maxLength?: number
  hasCharacterCount?: boolean
  className?: string
}

export const TextArea = <T extends FieldValues>(props: TextAreaProps<T>) => {
  const {
    form,
    name,
    placeholder,
    label,
    required,
    disabled,
    formItemProps,
    hint,
    maxLength,
    hasCharacterCount,
    className,
  } = props
  const isMobile = useIsMobile()

  const fieldValue = form.watch(name)
  const characterCount = useMemo(() => (fieldValue as string)?.length || 0, [fieldValue])

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
          <div>
            <FormLabel>
              {label}
              {required && <span className="text-red-500 ml-1">*</span>}
            </FormLabel>
            {hint && <p className="text-sm text-muted-foreground mt-1">{hint}</p>}
          </div>
          <div>
            <FormControl>
              <Textarea
                data-testid={`${name}TextArea`}
                data-test-element="formField"
                className={cn(
                  'disabled:opacity-100 disabled:border-hidden disabled:shadow-none disabled:min-h-4 disabled:py-0 disabled:resize-none disabled:pointer-events-none',
                  className,
                )}
                placeholder={placeholder}
                maxLength={maxLength}
                {...field}
                disabled={disabled}
              />
            </FormControl>
            <div className="flex justify-between mt-1">
              <FormMessage />
              {hasCharacterCount && maxLength && (
                <span className="text-sm text-muted-foreground ml-auto">
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
