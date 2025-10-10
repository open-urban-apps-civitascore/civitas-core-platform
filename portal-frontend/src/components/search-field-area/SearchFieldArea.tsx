import { ReactNode } from 'react'
import { SearchField, SearchFieldProps } from '../searchField/SearchField'

interface SearchHeaderProps extends SearchFieldProps {
  customElement?: ReactNode
}

export const SearchHeader = (props: SearchHeaderProps) => {
  const { customElement, ...searchFieldProps } = props
  return (
    <div className="flex justify-between items-end bg-muted  h-[calc(var(--search-height))] pb-[calc(--spacing(4))]">
      <SearchField {...searchFieldProps} />
      {customElement}
    </div>
  )
}
