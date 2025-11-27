import { ReactNode } from 'react'

import { cn } from '@/lib/utils'

import { SearchField, SearchFieldProps } from '../searchField/SearchField'

interface SearchHeaderProps extends SearchFieldProps {
  customElement?: ReactNode
}

export const SearchHeader = (props: SearchHeaderProps) => {
  const { customElement, className, ...searchFieldProps } = props
  return (
    <div
      className={cn('flex justify-between items-end h-[calc(var(--search-height))] pb-[calc(--spacing(4))]', className)}
    >
      <SearchField {...searchFieldProps} />
      {customElement}
    </div>
  )
}
