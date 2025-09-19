import { useTranslations } from 'next-intl'
import { Dispatch, InputHTMLAttributes, SetStateAction, useEffect, useState } from 'react'

import { Input } from '@/components/ui/input'
import { useDebounce } from '@/hooks/useDebounce'

interface SearchFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  setSearchString: Dispatch<SetStateAction<string>>
}

export const SearchField = (props: SearchFieldProps) => {
  const { setSearchString, ...inputProps } = props
  const t = useTranslations('common')
  const [input, setInput] = useState('')
  const debouncedInput = useDebounce(input, 300)

  useEffect(() => {
    setSearchString(debouncedInput)
  }, [debouncedInput, setSearchString])

  return (
    <Input
      {...inputProps}
      type="search"
      aria-label={inputProps['aria-label'] ?? t('search')}
      value={input}
      placeholder={inputProps.placeholder ?? `${t('search')}...`}
      onChange={event => setInput(event.target.value)}
    />
  )
}
