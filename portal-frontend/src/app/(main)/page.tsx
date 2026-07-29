'use client'

import { FileText } from 'lucide-react'
import Image from 'next/image'
import { useTranslations } from 'next-intl'

import { Button } from '@/components/ui/button'

import { RecommendedWorkflow } from './components/RecommendedWorkflow'

const Page = () => {
  const t = useTranslations('welcome')

  return (
    <div className="flex h-full flex-col overflow-auto">
      <div className="flex flex-col items-center justify-center gap-8 p-12 text-center">
        <Image
          src="/images/only_logo_civitas.svg"
          alt="Civitas Logo"
          width={200}
          height={231}
          className="w-40 sm:w-48 md:w-56"
        />
        <h1 className="text-2xl font-bold sm:text-3xl">{t('title')}</h1>

        <div>
          <p className="text-sm sm:text-base">{t('subtitle')}</p>
          <Button asChild variant="outline" className="mt-2">
            <a href="https://docs.core.civitasconnect.digital/" target="_blank" rel="noopener noreferrer">
              <FileText className="mr-2 h-4 w-4" />
              {t('docsButton')}
            </a>
          </Button>
        </div>
      </div>

      <RecommendedWorkflow />
    </div>
  )
}

export default Page
