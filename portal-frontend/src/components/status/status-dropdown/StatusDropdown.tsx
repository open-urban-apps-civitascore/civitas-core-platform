'use client'

import { ChevronDown, Info } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { STATUS_ICONS, StatusBadge } from '@/components/status/status-badge/StatusBadge'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { STATUS_TYPES, StatusTypes } from '@/types/common'

interface StatusDropdownProps<T extends StatusTypes> {
  status: T
  onStatusChange: (status: T) => void
  statusOptions: readonly T[]
  canStage?: boolean
  canSetDraft?: boolean
  canRelease?: boolean
  isReadOnly?: boolean
  statusHint?: string
  availableHint?: string
}
export const StatusDropdown = <T extends StatusTypes>(props: StatusDropdownProps<T>) => {
  const {
    status,
    onStatusChange,
    canStage = false,
    canSetDraft = true,
    canRelease = true,
    isReadOnly = false,
    statusOptions,
    statusHint,
    availableHint,
  } = props
  const t = useTranslations('common.status')

  const getStatusLabel = (statusType: T): string => {
    return t(statusType as Parameters<typeof t>[0])
  }

  const getStatusIcon = (statusType: StatusTypes) => {
    const Icon = STATUS_ICONS[statusType]
    return <Icon className="w-4 h-4 text-muted-foreground" />
  }

  return (
    <div className="flex items-center">
      {statusHint && (
        <Tooltip>
          <TooltipTrigger className="mr-2" asChild>
            <Info size={18} className="text-muted-foreground" />
          </TooltipTrigger>
          <TooltipContent>{statusHint}</TooltipContent>
        </Tooltip>
      )}
      {isReadOnly ? (
        <StatusBadge status={status} className="h-9" />
      ) : (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="outline"
              data-testid="statusDropdown"
              className="flex items-center gap-2 min-w-[140px] justify-between"
            >
              <div className="flex items-center gap-2">
                {getStatusIcon(status)}
                <span>{getStatusLabel(status)}</span>
              </div>
              <ChevronDown className="w-4 h-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {statusOptions.map(option => {
              // canStage means the content passes validation to leave DRAFT (stage to READY).
              // AVAILABLE additionally needs *_RELEASE permission (canRelease); READY only needs *_UPDATE (implied by edit mode).
              const isAvailableDisabled = option === STATUS_TYPES.AVAILABLE && (!canStage || !canRelease)
              const isReadyDisabled = option === STATUS_TYPES.READY && !canStage
              const isDisabled =
                isAvailableDisabled || isReadyDisabled || (option === STATUS_TYPES.DRAFT && !canSetDraft)

              const item = (
                <DropdownMenuItem
                  key={option}
                  data-testid={`statusOption-${option.toLowerCase()}`}
                  onClick={() => onStatusChange(option)}
                  disabled={isDisabled}
                  className={cn(
                    status === option && 'bg-accent',
                    (isAvailableDisabled || isReadyDisabled) && 'opacity-50 cursor-not-allowed',
                  )}
                >
                  <div className="flex items-center gap-2">
                    {getStatusIcon(option)}
                    <span>{getStatusLabel(option)}</span>
                  </div>
                </DropdownMenuItem>
              )

              if (isAvailableDisabled && availableHint) {
                return (
                  <Tooltip key={option}>
                    <TooltipTrigger asChild>
                      <div>{item}</div>
                    </TooltipTrigger>
                    <TooltipContent>{availableHint}</TooltipContent>
                  </Tooltip>
                )
              }

              return item
            })}
          </DropdownMenuContent>
        </DropdownMenu>
      )}
    </div>
  )
}
