'use client'

import { ChevronDown, CircleCheckBig, CircleDashed } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import { STATUS_TYPES } from '@/types/common'
import { DatasourceStatusType } from '@/types/datasources'

interface StatusDropdownProps {
  status: DatasourceStatusType
  onStatusChange: (status: DatasourceStatusType) => void
  canSetAvailable?: boolean
}

export const StatusDropdown = (props: StatusDropdownProps) => {
  const { status, onStatusChange, canSetAvailable = false } = props
  const t = useTranslations('common.status')

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
            {status === STATUS_TYPES.DRAFT ? (
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
          onClick={() => onStatusChange(STATUS_TYPES.DRAFT)}
          className={cn(status === STATUS_TYPES.DRAFT && 'bg-accent')}
        >
          <CircleDashed className="w-4 h-4 text-muted-foreground mr-2" />
          {getStatusLabel(STATUS_TYPES.DRAFT)}
        </DropdownMenuItem>
        <DropdownMenuItem
          data-testid="statusOption-available"
          onClick={() => canSetAvailable && onStatusChange(STATUS_TYPES.AVAILABLE)}
          disabled={!canSetAvailable}
          className={cn(
            status === STATUS_TYPES.AVAILABLE && 'bg-accent',
            !canSetAvailable && 'opacity-50 cursor-not-allowed',
          )}
        >
          <CircleCheckBig className="w-4 h-4 text-muted-foreground mr-2" />
          {getStatusLabel(STATUS_TYPES.AVAILABLE)}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
