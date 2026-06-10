'use client'

import { ChevronDown } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'

interface VisualizationListProps {
  datasetId: string
}

export const VisualizationList = ({ datasetId }: VisualizationListProps) => {
  const t = useTranslations('datasets.overview.completion.visualizations')

  return (
    <div>
      <div className="flex justify-end">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline">
              {t('addButton')}
              <ChevronDown className="ml-2 h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem asChild>
              <GuardedLink href={`/datasets/${datasetId}/visualizations?type=dashboard`} className="flex items-center gap-3">
                <span>{t('dashboard')}</span>
                <span className="text-xs text-muted-foreground">{t('dashboardSubtitle')}</span>
              </GuardedLink>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <p className="mt-2 text-sm font-normal">{t('empty')}</p>
    </div>
  )
}
