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

interface ComboboxMultiSelectProps {
  items: string[]
  hasSelectAllOption?: boolean
  // makes it a controlled component
  onValueChange?: (newValues: string[]) => void
  testId?: string
  disabled?: ComboboxTriggerProps['disabled']
  isInvalid?: boolean
  className?: string
}
export const ComboboxMultiSelect = (props: ComboboxMultiSelectProps) => {
  const { items, hasSelectAllOption, onValueChange, testId, disabled, isInvalid, className } = props
  const anchor = useComboboxAnchor()
  const [selected, setSelected] = React.useState<string[]>([items[0]])

  const handleValueChange = (newValues: string[]) => {
    if (newValues.includes('select_all') && !selected.includes('select_all')) {
      setSelected(['select_all', ...items])
      onValueChange?.([...items])
    } else if (selected.includes('select_all') && !newValues.includes('select_all')) {
      setSelected([])
      onValueChange?.([])
    } else {
      const selectedValues = newValues.filter(v => v !== 'select_all')
      const areAllValuesSelected = items.every(f => selectedValues.includes(f))
      setSelected(areAllValuesSelected ? ['select_all', ...selectedValues] : selectedValues)
      onValueChange?.([...selectedValues])
    }
  }

  return (
    <Combobox
      data-testid={testId}
      multiple
      autoHighlight
      items={items}
      value={selected}
      onValueChange={hasSelectAllOption ? handleValueChange : onValueChange}
      disabled={disabled}
    >
      <ComboboxChips ref={anchor} className={cn('w-full', className)}>
        <ComboboxValue>
          {(values: string[]) => (
            <React.Fragment>
              {values
                .filter(value => value !== 'select_all')
                .map((value: string) => (
                  <ComboboxChip key={value} aria-invalid={isInvalid}>
                    {value}
                  </ComboboxChip>
                ))}
              <ComboboxChipsInput />
            </React.Fragment>
          )}
        </ComboboxValue>
      </ComboboxChips>
      <ComboboxContent anchor={anchor}>
        <ComboboxEmpty>No items found.</ComboboxEmpty>
        <ComboboxList>
          {hasSelectAllOption && (
            <>
              <ComboboxItem key="selectAll" value="select_all">
                Select All
              </ComboboxItem>
              <Separator />
            </>
          )}
          {items.map(item => (
            <ComboboxItem key={item} value={item}>
              {item}
            </ComboboxItem>
          ))}
        </ComboboxList>
      </ComboboxContent>
    </Combobox>
  )
}
