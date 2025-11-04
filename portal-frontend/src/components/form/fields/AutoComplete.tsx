import { Check } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { JSX, useEffect } from 'react'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { FormField, FormMessage } from '@/components/ui/form'
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
  input: string
  minLength?: number
  className?: string
  popoverContentProps?: {
    className?: string
  }
  onOpenChange: (open: boolean) => void
  onInputChange: (input: string) => void
  onSelectItem: (newSelection: SelectItem) => void
}
export const AutoComplete = <T extends FieldValues>(props: AutoCompleteProps<T>) => {
  const {
    id,
    isOpen,
    listItems,
    input,
    form,
    name,
    placeholder,
    label,
    required = false,
    minLength = 3,
    className,
    onOpenChange,
    onInputChange,
    onSelectItem,
    popoverContentProps,
  } = props
  const t = useTranslations('common')

  return (
    <Popover open={isOpen} onOpenChange={onOpenChange} modal={false}>
      <Command className={className} filter={() => 1}>
        <label htmlFor={id} className="text-sm font-medium text-gray-700 dark:text-gray-200">
          {label}
          {required && <span className="text-red-500 ml-1">*</span>}
        </label>
        <PopoverTrigger asChild>
          <CommandInput
            id={id}
            placeholder={placeholder}
            className="h-9"
            onValueChange={onInputChange}
            wrapperProps={{ className: 'border rounded-md' }}
          />
        </PopoverTrigger>
        <PopoverContent
          className={cn('w-full p-1.5', popoverContentProps?.className)}
          onOpenAutoFocus={e => e.preventDefault()}
          onCloseAutoFocus={e => e.preventDefault()}
        >
          <FormField
            control={form.control}
            name={name}
            rules={{ required: required ? t('errors.required') : false }}
            render={({ field }) => (
              <div>
                <CommandList>
                  <CommandEmpty>
                    {input.length < minLength
                      ? t('errors.minChar', { amount: minLength.toString() })
                      : t('errors.notFound', { items: label })}
                  </CommandEmpty>
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
                <FormMessage />
              </div>
            )}
          />
        </PopoverContent>
      </Command>
    </Popover>
  )
}
