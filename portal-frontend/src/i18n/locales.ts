import { de, enUS, Locale as DateFnsLocale } from 'date-fns/locale'

export const LOCALES = [
  {
    name: 'german',
    key: 'de',
  },
  {
    name: 'english',
    key: 'en',
  },
] as const

export type AppLocale = (typeof LOCALES)[number]['key']

export const DATE_LOCALES: Record<AppLocale, DateFnsLocale> = {
  de: de,
  en: enUS,
}
