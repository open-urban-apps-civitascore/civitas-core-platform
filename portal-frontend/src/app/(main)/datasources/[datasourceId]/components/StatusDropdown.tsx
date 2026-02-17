'use client'

import { ChevronDown, CircleCheckBig, CircleDashed } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { DATASOURCE_STATUS_TYPES } from '@/const/connectors'
import { cn } from '@/lib/utils'
import { DatasourceStatusType } from '@/types/datasources'

interface StatusDropdownProps {
  status: DatasourceStatusType
  onStatusChange: (status: DatasourceStatusType) => void
  canSetAvailable?: boolean
}

export const StatusDropdown = (props: StatusDropdownProps) => {
  const { status, onStatusChange, canSetAvailable = false } = props
  const t = useTranslations('datasources.status')

  const getStatusLabel = (statusType: DatasourceStatusType): string => {
    return t(statusType)
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="outline"
          data-testid="statusDropdown"
          className="flex items-center gap-2 min-w-[140px] justify-between"
        >
          <div className="flex items-center gap-2">
            {status === DATASOURCE_STATUS_TYPES.DRAFT ? (
              <CircleDashed className="w-4 h-4 text-muted-foreground" />
            ) : (
              <CircleCheckBig className="w-4 h-4 text-muted-foreground" />
            )}
            <span>{getStatusLabel(status)}</span>
          </div>
          <ChevronDown className="w-4 h-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem
          data-testid="statusOption-draft"
          onClick={() => onStatusChange(DATASOURCE_STATUS_TYPES.DRAFT)}
          className={cn(status === DATASOURCE_STATUS_TYPES.DRAFT && 'bg-accent')}
        >
          <CircleDashed className="w-4 h-4 text-muted-foreground mr-2" />
          {getStatusLabel(DATASOURCE_STATUS_TYPES.DRAFT)}
        </DropdownMenuItem>
        <DropdownMenuItem
          data-testid="statusOption-available"
          onClick={() => canSetAvailable && onStatusChange(DATASOURCE_STATUS_TYPES.AVAILABLE)}
          disabled={!canSetAvailable}
          className={cn(
            status === DATASOURCE_STATUS_TYPES.AVAILABLE && 'bg-accent',
            !canSetAvailable && 'opacity-50 cursor-not-allowed',
          )}
        >
          <CircleCheckBig className="w-4 h-4 text-muted-foreground mr-2" />
          {getStatusLabel(DATASOURCE_STATUS_TYPES.AVAILABLE)}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
