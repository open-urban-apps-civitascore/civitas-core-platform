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
  id?: string
  testId?: string
  disabled?: ComboboxTriggerProps['disabled']
  isInvalid?: boolean
  className?: string
}
const SELECT_ALL = '__combobox_internal_select_all__'

export const ComboboxMultiSelect = (props: ComboboxMultiSelectProps) => {
  const { items, value, hasSelectAllOption, placeholder, onValueChange, id, testId, disabled, isInvalid, className } =
    props
  const anchor = useComboboxAnchor()

  const labelByValue = React.useMemo(() => Object.fromEntries(items.map(o => [o.value, o.label])), [items])
  const allValues = React.useMemo(() => items.map(o => o.value), [items])

  const hasSentinelCollision = React.useMemo(() => allValues.includes(SELECT_ALL), [allValues])

  const selected = React.useMemo(() => {
    const areAllSelected = allValues.length > 0 && allValues.every(v => value.includes(v))
    return areAllSelected ? [SELECT_ALL, ...value] : value
  }, [value, allValues])

  const handleValueChange = (newValues: string[]) => {
    if (newValues.includes(SELECT_ALL) && !selected.includes(SELECT_ALL)) {
      onValueChange?.([...allValues])
    } else if (selected.includes(SELECT_ALL) && !newValues.includes(SELECT_ALL)) {
      onValueChange?.([])
    } else {
      onValueChange?.(newValues.filter(v => v !== SELECT_ALL))
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
      <ComboboxChips ref={anchor} aria-invalid={isInvalid} className={cn('w-full', className)}>
        <ComboboxValue>
          {(values: string[]) => (
            <React.Fragment>
              {values
                .filter(v => v !== SELECT_ALL)
                .map((v: string) => (
                  <ComboboxChip key={v} showRemove={!disabled}>
                    {labelByValue[v] ?? v}
                  </ComboboxChip>
                ))}
              <ComboboxChipsInput
                id={id}
                placeholder={values.filter(v => v !== SELECT_ALL).length === 0 ? placeholder : undefined}
              />
            </React.Fragment>
          )}
        </ComboboxValue>
      </ComboboxChips>
      <ComboboxContent anchor={anchor}>
        <ComboboxEmpty>No items found.</ComboboxEmpty>
        <ComboboxList>
          {hasSelectAllOption && items.length > 0 && !hasSentinelCollision && (
            <>
              <ComboboxItem key="selectAll" value={SELECT_ALL}>
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
