'use client'

import Link from 'next/link'
import { useTranslations } from 'next-intl'

const NotFound = () => {
  const t = useTranslations('common')

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background text-foreground">
      <h1 className="text-4xl font-semibold">404</h1>
      <p className="text-muted-foreground">{t('errors.pageNotFound')}</p>
      <Link href="/" className="text-primary underline hover:text-primary/80">
        {t('errors.backToHome')}
      </Link>
    </div>
  )
}

export default NotFound
