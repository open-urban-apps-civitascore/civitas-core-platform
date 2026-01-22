import { Check } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { JSX } from 'react'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { FormField, FormLabel, FormMessage } from '@/components/ui/form'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'

export type SelectItem = { value: string; label: JSX.Element | string }

interface AutoCompleteProps<T extends FieldValues> {
  id: string
  isOpen: boolean
  listItems: SelectItem[]
  form: UseFormReturn<T>
  name: Path<T>
  placeholder: string
  label: string
  // eslint-disable-next-line react/boolean-prop-naming
  required?: boolean
  inputValue: string
  minLength?: number
  className?: string
  popoverContentProps?: {
    className?: string
  }
  onOpenChange: (open: boolean) => void
  onInputChange: (input: string) => void
  onSelectItem: (newSelection: SelectItem) => void
  onBlur?: () => void
  isLoading?: boolean
}
export const AutoComplete = <T extends FieldValues>(props: AutoCompleteProps<T>) => {
  const {
    id,
    isOpen,
    listItems,
    inputValue,
    form,
    name,
    placeholder,
    label,
    required = false,
    minLength = 2,
    className,
    onOpenChange,
    onInputChange,
    onSelectItem,
    onBlur,
    popoverContentProps,
    isLoading,
  } = props
  const t = useTranslations('common')
  const error =
    inputValue.trim().length < minLength
      ? t('errors.minChar', { amount: minLength.toString() })
      : t('errors.notFound', { items: label })

  return (
    <FormField
      control={form.control}
      name={name}
      rules={{ required: required ? t('errors.required') : false }}
      render={({ field }) => (
        <Popover open={isOpen} onOpenChange={onOpenChange} modal={false}>
          <Command className={className} filter={() => 1}>
            <FormLabel htmlFor={id} className="text-sm font-medium text-gray-700 dark:text-gray-200">
              {label}
              {required && <span className="text-red-500 ml-1">*</span>}
            </FormLabel>
            <div>
              <PopoverTrigger asChild>
                <CommandInput
                  id={id}
                  placeholder={placeholder}
                  className="h-9"
                  onValueChange={onInputChange}
                  wrapperProps={{
                    className: cn('border rounded-md', form.formState.errors[name] && 'border-destructive'),
                  }}
                  value={inputValue}
                />
              </PopoverTrigger>
              <FormMessage className="mt-2" />
            </div>
            <PopoverContent
              className={cn('w-full p-1.5', popoverContentProps?.className)}
              onOpenAutoFocus={e => e.preventDefault()}
              onCloseAutoFocus={e => e.preventDefault()}
              onInteractOutside={event => {
                if (onBlur) {
                  const target = event.target as HTMLElement
                  if (target.id !== '[data-radix-popover-trigger]') onBlur()
                }
              }}
            >
              <div>
                <CommandList>
                  <CommandEmpty>{isLoading ? <LoadingSpinner /> : error}</CommandEmpty>
                  <CommandGroup className="p-0 w-full">
                    {listItems.map(item => (
                      <CommandItem
                        className="p-x-1.5 p-y-2"
                        key={item.value}
                        value={item.value}
                        onSelect={() => {
                          onSelectItem(item)
                          onOpenChange(false)
                        }}
                      >
                        {item.label}
                        <Check className={cn('ml-auto', field.value === item.value ? 'opacity-100' : 'opacity-0')} />
                      </CommandItem>
                    ))}
                  </CommandGroup>
                </CommandList>
              </div>
            </PopoverContent>
          </Command>
        </Popover>
      )}
    />
  )
}
