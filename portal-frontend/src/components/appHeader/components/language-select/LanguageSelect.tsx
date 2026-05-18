'use client'

import { useRouter } from 'next/navigation'
import { useLocale, useTranslations } from 'next-intl'

import { BasicSelect } from '@/components/basicSelect/BasicSelect'
import { LOCALES } from '@/i18n/locales'

export const LanguageSelect = () => {
  const tLang = useTranslations('languages')
  const router = useRouter()
  const initialLocale = useLocale()
  const handleLanguageChange = (locale: string) => {
    document.cookie = `NEXT_LOCALE=${locale}; path=/; max-age=31536000` // 1 year valid
    router.refresh() // Reload with new locale
  }

  const options = LOCALES.map(language => ({
    value: language.key,
    label: tLang(language.name),
  }))

  return (
    <BasicSelect
      onValueChange={handleLanguageChange}
      options={options}
      placeholder={tLang('selectLanguagePlaceholder')}
      triggerClassName="w-[140px]"
      value={initialLocale}
    />
  )
}
