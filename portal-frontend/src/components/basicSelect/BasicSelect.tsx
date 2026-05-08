import { HTMLAttributes } from 'react'

import { SelectOption } from '@/types/common'

import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from '../ui/select'

interface BasicSelectProps extends HTMLAttributes<HTMLDivElement> {
  onValueChange: (value: string) => void
  options: SelectOption[]
  placeholder?: string
  defaultValue?: SelectOption['value']
  triggerClassName?: string
  contentClassName?: string
  // eslint-disable-next-line react/boolean-prop-naming
  disabled?: boolean
  size?: 'sm' | 'default'
}

export const BasicSelect = (props: BasicSelectProps) => {
  const {
    onValueChange,
    options,
    placeholder,
    triggerClassName,
    contentClassName,
    defaultValue,
    disabled = false,
    size,
  } = props

  return (
    <Select onValueChange={value => onValueChange(value)} defaultValue={defaultValue}>
      <SelectTrigger className={`w-[235px] ${triggerClassName}`} disabled={disabled} size={size}>
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent className={`max-h-[350px] overflow-y-auto ${contentClassName}`}>
        <SelectGroup>
          {options.map(role => (
            <SelectItem key={role.value} value={role.value}>
              {role.label}
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </Select>
  )
}
