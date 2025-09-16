import { useTranslations } from 'next-intl'
import { Dispatch, HTMLAttributes, SetStateAction, useEffect, useState } from 'react'

import { Input } from '@/components/ui/input'
import { useDebounce } from '@/hooks/useDebounce'

interface SearchFieldProps extends HTMLAttributes<HTMLInputElement> {
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
      type="text"
      value={input}
      placeholder={`${t('search')}...`}
      onChange={event => setInput(event.target.value)}
    />
  )
}
