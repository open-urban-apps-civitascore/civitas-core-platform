'use client'

import { Loader2 } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { HTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

export const LoadingSpinner = (props: HTMLAttributes<HTMLDivElement>) => {
  const { className } = props
  const t = useTranslations('common')
  console.log('loading spinner')
  return (
    <div className={cn('flex items-center justify-center p-8', className)}>
      <Loader2 className="h-8 w-8 animate-spin" />
      <span className="ml-2">{t('loading')}</span>
    </div>
  )
}
