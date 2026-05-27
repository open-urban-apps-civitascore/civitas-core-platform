'use client'

import { ComboboxTriggerProps } from '@base-ui/react'
import * as React from 'react'

import {
  Combobox,
  ComboboxChip,
  ComboboxChips,
  ComboboxChipsInput,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxItem,
  ComboboxList,
  ComboboxValue,
  useComboboxAnchor,
} from '@/components/ui/combobox'
import { Separator } from '@/components/ui/separator'
import { cn } from '@/lib/utils'
import { SelectOption } from '@/types/common'

interface ComboboxMultiSelectProps {
  items: SelectOption[]
  value: string[]
  hasSelectAllOption?: boolean
  placeholder?: string
  onValueChange?: (newValues: string[]) => void
  testId?: string
  disabled?: ComboboxTriggerProps['disabled']
  isInvalid?: boolean
  className?: string
}
export const ComboboxMultiSelect = (props: ComboboxMultiSelectProps) => {
  const { items, value, hasSelectAllOption, placeholder, onValueChange, testId, disabled, isInvalid, className } = props
  const anchor = useComboboxAnchor()

  const labelByValue = React.useMemo(() => Object.fromEntries(items.map(o => [o.value, o.label])), [items])
  const allValues = React.useMemo(() => items.map(o => o.value), [items])

  const selected = React.useMemo(() => {
    const areAllSelected = allValues.length > 0 && allValues.every(v => value.includes(v))
    return areAllSelected ? ['select_all', ...value] : value
  }, [value, allValues])

  const handleValueChange = (newValues: string[]) => {
    if (newValues.includes('select_all') && !selected.includes('select_all')) {
      onValueChange?.([...allValues])
    } else if (selected.includes('select_all') && !newValues.includes('select_all')) {
      onValueChange?.([])
    } else {
      onValueChange?.(newValues.filter(v => v !== 'select_all'))
    }
  }

  return (
    <Combobox
      data-testid={testId}
      multiple
      autoHighlight
      items={allValues}
      value={selected}
      onValueChange={handleValueChange}
      disabled={disabled}
    >
      <ComboboxChips ref={anchor} className={cn('w-full', className)}>
        <ComboboxValue>
          {(values: string[]) => (
            <React.Fragment>
              {values
                .filter(v => v !== 'select_all')
                .map((v: string) => (
                  <ComboboxChip key={v} aria-invalid={isInvalid}>
                    {labelByValue[v] ?? v}
                  </ComboboxChip>
                ))}
              <ComboboxChipsInput
                placeholder={values.filter(v => v !== 'select_all').length === 0 ? placeholder : undefined}
              />
            </React.Fragment>
          )}
        </ComboboxValue>
      </ComboboxChips>
      <ComboboxContent anchor={anchor}>
        <ComboboxEmpty>No items found.</ComboboxEmpty>
        <ComboboxList>
          {hasSelectAllOption && items.length > 0 && (
            <>
              <ComboboxItem key="selectAll" value="select_all">
                Select All
              </ComboboxItem>
              <Separator />
            </>
          )}
          {items.map(item => (
            <ComboboxItem key={item.value} value={item.value}>
              {item.label}
            </ComboboxItem>
          ))}
        </ComboboxList>
      </ComboboxContent>
    </Combobox>
  )
}
