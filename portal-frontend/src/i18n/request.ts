import { cookies, headers } from 'next/headers'
import { getRequestConfig } from 'next-intl/server'

import { routing } from './config'
import { AppLocale } from './locales'

// get locale from 1. cookie, 2. accept-language header or 3. default value
const getLocale = async () => {
  const store = await cookies()
  const cookieLocale = store.get('NEXT_LOCALE')?.value
  const headerLocale = (await headers())
    .get('accept-language')
    ?.split(',')[0] // e.g. "de-DE,en;q=0.9" → "de-DE"
    ?.split(';')[0] // e.g. en;q=0.9 -> "en"
    ?.split('-')[0] // e.g. "de-DE" -> "de"
  const defaultLocale = routing.defaultLocale

  if (cookieLocale && (routing.locales as string[]).includes(cookieLocale)) {
    return cookieLocale as AppLocale
  } else if (headerLocale && (routing.locales as string[]).includes(headerLocale)) {
    return headerLocale as AppLocale
  } else return defaultLocale
}

// returns translations depending on locale
export default getRequestConfig(async () => {
  const locale = await getLocale()

  const messages = (await import(`../messages/${locale}.json`)).default

  return {
    locale,
    messages,
  }
})
