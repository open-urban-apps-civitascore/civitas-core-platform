'use client'

import { ChevronDown, CircleCheckBig, CircleCheckIcon, CircleDashed } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import { STATUS_TYPES, StatusTypes } from '@/types/common'

interface StatusDropdownProps<T extends StatusTypes> {
  status: T
  onStatusChange: (status: T) => void
  statusOptions: readonly T[]
  canSetAvailable?: boolean
  isReadOnly?: boolean
}
export const StatusDropdown = <T extends StatusTypes>(props: StatusDropdownProps<T>) => {
  const { status, onStatusChange, canSetAvailable = false, isReadOnly = false, statusOptions } = props
  const t = useTranslations('common.status')

  const getStatusLabel = (statusType: T): string => {
    return t(statusType as Parameters<typeof t>[0])
  }

  const getStatusIcon = (statusType: StatusTypes) => {
    switch (statusType) {
      case STATUS_TYPES.DRAFT:
        return <CircleDashed className="w-4 h-4 text-muted-foreground" />
      case STATUS_TYPES.AVAILABLE:
        return <CircleCheckBig className="w-4 h-4 text-muted-foreground" />
      case STATUS_TYPES.READY:
        return <CircleCheckIcon className="w-4 h-4 text-muted-foreground" />
      default:
        return null
    }
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="outline"
          data-testid="statusDropdown"
          className="flex items-center gap-2 min-w-[140px] justify-between"
          disabled={isReadOnly}
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
        {statusOptions.map(option => (
          <DropdownMenuItem
            key={option}
            data-testid={`statusOption-${option.toLowerCase()}`}
            onClick={() => onStatusChange(option)}
            disabled={
              (option === STATUS_TYPES.AVAILABLE && !canSetAvailable) ||
              (option === STATUS_TYPES.READY && !canSetAvailable)
            }
            className={cn(
              status === option && 'bg-accent',
              (option === STATUS_TYPES.AVAILABLE && !canSetAvailable) ||
                (option === STATUS_TYPES.READY && !canSetAvailable && 'opacity-50 cursor-not-allowed'),
            )}
          >
            <div className="flex items-center gap-2">
              {getStatusIcon(option)}
              <span>{getStatusLabel(option)}</span>
            </div>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
