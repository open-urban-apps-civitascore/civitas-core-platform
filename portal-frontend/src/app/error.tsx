'use client'

import { AlertTriangle } from 'lucide-react'
import Link from 'next/link'
import { useTranslations } from 'next-intl'
import { useEffect } from 'react'

import { Button } from '@/components/ui/button'

interface ErrorPageProps {
  error: Error & { digest?: string }
}

const Error = ({ error }: ErrorPageProps) => {
  const t = useTranslations('common')

  useEffect(() => {
    console.error(error)
  }, [error])

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background text-foreground px-4">
      <AlertTriangle className="size-10 text-muted-foreground" />
      <h1 className="text-2xl font-semibold text-center">{t('errors.genericErrorTitle')}</h1>
      <p className="text-muted-foreground text-center">{t('errors.genericErrorSubtitle')}</p>
      <div className="flex gap-4 mt-4">
        <Button variant="outline" onClick={() => window.location.reload()}>
          {t('errors.tryAgain')}
        </Button>
        <Button asChild>
          <Link href="/">{t('errors.goToHomePage')}</Link>
        </Button>
      </div>
    </div>
  )
}

export default Error
