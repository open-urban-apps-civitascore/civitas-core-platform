import { useTranslations } from 'next-intl'
import { InputHTMLAttributes, useEffect, useRef, useState } from 'react'

import { Input } from '@/components/ui/input'
import { useDebounce } from '@/hooks/useDebounce'

export interface SearchFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  searchString: string
  onChangeSearchString: (seachString: string) => void
}

export const SearchField = (props: SearchFieldProps) => {
  const { searchString, onChangeSearchString, ...inputProps } = props
  const t = useTranslations('common')
  const [input, setInput] = useState(searchString)
  const debouncedInput = useDebounce(input, 300)

  const onChangeSearchStringRef = useRef(onChangeSearchString)
  useEffect(() => {
    onChangeSearchStringRef.current = onChangeSearchString
  }, [onChangeSearchString])

  useEffect(() => {
    onChangeSearchStringRef.current(debouncedInput)
  }, [debouncedInput])

  return (
    <div role="search" className="flex items-center w-xs">
      <Input
        {...inputProps}
        className="bg-white"
        type="search"
        aria-label={inputProps['aria-label'] ?? t('search')}
        value={input}
        placeholder={inputProps.placeholder ?? `${t('search')}...`}
        onChange={event => setInput(event.target.value)}
      />
    </div>
  )
}
