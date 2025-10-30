import { Check } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { FormField, FormMessage } from '@/components/ui/form'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'

export type SelectItem = { value: string; label: string }
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
  onOpenChange: (open: boolean) => void
  onInputChange: (input: string) => void
  onSelectItem: (newSelection: SelectItem) => void
}
export const AutoComplete = <T extends FieldValues>(props: AutoCompleteProps<T>) => {
  const {
    isOpen,
    onOpenChange,
    listItems,
    onSelectItem,
    input,
    form,
    name,
    placeholder,
    label,
    required = false,
    id,
    onInputChange,
  } = props
  const t = useTranslations('common')

  return (
    <Popover open={isOpen} onOpenChange={onOpenChange} modal={false}>
      <Command filter={() => 1}>
        <label htmlFor={id} className="text-sm font-medium text-gray-700 dark:text-gray-200">
          {label}
          {required && <span className="text-red-500 ml-1">*</span>}
        </label>
        <PopoverTrigger asChild>
          <CommandInput id={id} placeholder={placeholder} className="h-9" onValueChange={onInputChange} />
        </PopoverTrigger>
        <PopoverContent
          className="w-[200px] p-0"
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
                    {input.length < 3 ? 'Please type more than 3 characters' : 'No contacts found'}
                  </CommandEmpty>
                  <CommandGroup>
                    {listItems.map(item => (
                      <CommandItem
                        key={item.value}
                        value={item.value}
                        onSelect={() => {
                          onSelectItem(item)
                          onOpenChange(false)
                        }}
                      >
                        {item.label} Test
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
