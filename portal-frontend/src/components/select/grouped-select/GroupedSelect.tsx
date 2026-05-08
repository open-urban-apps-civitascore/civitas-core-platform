import React, { HTMLAttributes } from 'react'

import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { SelectOption } from '@/types/common'

export type GroupedOption = {
  label: string
  options: SelectOption[]
}

interface GroupedSelectProps extends HTMLAttributes<HTMLDivElement> {
  onValueChange: (value: string) => void
  groupedOptions: GroupedOption[]
  placeholder?: string
  defaultValue?: SelectOption['value']
  triggerClassName?: string
  contentClassName?: string
  // eslint-disable-next-line react/boolean-prop-naming
  disabled?: boolean
  size?: 'sm' | 'default'
}

export const GroupedSelect = (props: GroupedSelectProps) => {
  const {
    groupedOptions,
    onValueChange,
    placeholder,
    defaultValue,
    triggerClassName,
    contentClassName,
    disabled,
    size,
  } = props
  return (
    <Select onValueChange={onValueChange} disabled={disabled} defaultValue={defaultValue}>
      <SelectTrigger className={triggerClassName} size={size}>
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent className={contentClassName}>
        {groupedOptions.map(groupedOption => {
          return (
            <SelectGroup key={groupedOption.label}>
              <SelectLabel>{groupedOption.label}</SelectLabel>
              {groupedOption.options.map(option => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectGroup>
          )
        })}
      </SelectContent>
    </Select>
  )
}
