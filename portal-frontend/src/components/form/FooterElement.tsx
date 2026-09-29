import { useTranslations } from 'next-intl'
import React from 'react'

interface FooterElementProps {
  areAllFieldsRequired?: boolean
}

export const FooterElement = ({ areAllFieldsRequired = false }: FooterElementProps) => {
  const t = useTranslations('common.info')
  return (
    <div>
      <span className="text-red-600">*&nbsp;</span>
      <span className="text-muted-foreground">{t(areAllFieldsRequired ? 'requiredField' : 'requiredInAvailable')}</span>
    </div>
  )
}
