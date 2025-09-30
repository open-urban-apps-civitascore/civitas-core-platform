export const formatDate = (dateString: string, locale: string): string => {
  const date = new Date(dateString)

  const dateLocale = locale === 'de' ? 'de-DE' : 'en-GB'

  const formattedDate = date.toLocaleDateString(dateLocale, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })

  return formattedDate
}
