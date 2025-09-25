import { useTranslations } from 'next-intl'
import { InputHTMLAttributes, useEffect, useState } from 'react'

import { Input } from '@/components/ui/input'
import { useDebounce } from '@/hooks/useDebounce'

interface SearchFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  searchString: string
  onChangeSearchString: (seachString: string) => void
}

export const SearchField = (props: SearchFieldProps) => {
  const { searchString, onChangeSearchString, ...inputProps } = props
  const t = useTranslations('common')
  const [input, setInput] = useState(searchString)
  const debouncedInput = useDebounce(input, 300)

  useEffect(() => {
    onChangeSearchString(debouncedInput)
  }, [debouncedInput])

  return (
    <div role="search" className="flex items-center h-[calc(var(--search-height))] w-xs">
      <Input
        {...inputProps}
        type="search"
        aria-label={inputProps['aria-label'] ?? t('search')}
        value={input}
        placeholder={inputProps.placeholder ?? `${t('search')}...`}
        onChange={event => setInput(event.target.value)}
      />
    </div>
  )
}
