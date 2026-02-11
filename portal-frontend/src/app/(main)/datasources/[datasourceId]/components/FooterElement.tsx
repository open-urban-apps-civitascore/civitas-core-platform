import { useTranslations } from 'next-intl'
import React from 'react'

export const FooterElement = () => {
  const t = useTranslations('common.info')
  return (
    <div>
      <span className="text-red-600">*&nbsp;</span>
      <span className="text-muted-foreground">{t('requiredInAvailable')}</span>
    </div>
  )
}
