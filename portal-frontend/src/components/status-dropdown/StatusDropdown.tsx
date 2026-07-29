'use client'

import { ChevronDown, CircleCheckBig, CircleCheckIcon, CircleDashed, Info } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import { STATUS_TYPES, StatusTypes } from '@/types/common'

import { Tooltip, TooltipContent, TooltipTrigger } from '../ui/tooltip'

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
    <div className="flex items-center">
      {statusHint && (
        <Tooltip>
          <TooltipTrigger className="mr-2" asChild>
            <Info size={18} className="text-muted-foreground" />
          </TooltipTrigger>
          <TooltipContent>{statusHint}</TooltipContent>
        </Tooltip>
      )}
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="outline"
            data-testid="statusDropdown"
            className="flex items-center gap-2 min-w-[140px] justify-between"
            disabled={isReadOnly}
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
            const isDisabled = isAvailableDisabled || isReadyDisabled || (option === STATUS_TYPES.DRAFT && !canSetDraft)

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
    </div>
  )
}
