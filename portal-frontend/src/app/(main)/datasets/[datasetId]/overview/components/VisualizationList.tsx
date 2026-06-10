'use client'

import { ChevronDown } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Visualization } from '@/types/visualizations'

import { VisualizationCard } from './VisualizationCard'

interface VisualizationListProps {
  datasetId: string
  visualizations: Visualization[]
  canEdit: boolean
  isOpenDataAccess: boolean
}

export const VisualizationList = ({ datasetId, visualizations, canEdit, isOpenDataAccess }: VisualizationListProps) => {
  const t = useTranslations('datasets.overview.completion.visualizations')
  const visualizationsDummyData = [
    {
      id: '1',
      name: 'Visualization 1',
      type: 'dashboard',
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
    },
    {
      id: '2',
      name: 'Visualization 2',
      type: 'dashboard',
      createdAt: '2024-01-02T00:00:00Z',
      updatedAt: '2024-01-02T00:00:00Z',
    },
  ]
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
              <GuardedLink
                href={`/datasets/${datasetId}/visualizations?type=dashboard`}
                className="flex items-center gap-3"
              >
                <span>{t('dashboard')}</span>
                <span className="text-xs text-muted-foreground">{t('dashboardSubtitle')}</span>
              </GuardedLink>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {visualizationsDummyData.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
          {visualizationsDummyData.map(visualization => (
            <VisualizationCard
              key={visualization.id}
              visualization={visualization}
              datasetId={datasetId}
              canEdit={canEdit}
              isOpenDataAccess={isOpenDataAccess}
            />
          ))}
        </div>
      ) : (
        <p className="mt-4 text-sm font-normal">{t('empty')}</p>
      )}
    </div>
  )
}
