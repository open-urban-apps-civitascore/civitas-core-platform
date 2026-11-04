import { ChevronsDownUp, ChevronsUpDown, Trash2 } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { get, UseFormReturn, useWatch } from 'react-hook-form'

import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { API_CALL_SECTIONS, HTTP_METHODS } from '@/const/connectors'
import { cn } from '@/lib/utils'
import { ApiCallSection, HttpApiCallsForm } from '@/types/httpApiCalls'
import { formatPathWithQuery } from '@/utils/connectors'

import { ApiCallPagination } from './ApiCallPagination'
import { ApiCallRequest } from './ApiCallRequest'

interface ApiCallItemProps {
  form: UseFormReturn<HttpApiCallsForm>
  index: number
  isEditing: boolean
  collapsedSections: ReadonlySet<ApiCallSection>
  isDisabled: boolean
  dataStructureName?: string
  onEdit: () => void
  onCollapsedSectionsChange: (collapsedSections: ReadonlySet<ApiCallSection>) => void
  onDelete: () => void
  onClose: () => void
  onCancel: () => void
}

export const ApiCallItem = (props: ApiCallItemProps) => {
  const {
    form,
    index,
    isEditing,
    collapsedSections,
    isDisabled,
    dataStructureName,
    onEdit,
    onCollapsedSectionsChange,
    onDelete,
    onClose,
    onCancel,
  } = props
  const t = useTranslations('datasources.apiCallsTab')
  const tActions = useTranslations('common.actions')

  const apiCallPath = `configuration.apiCalls.${index}` as const
  const apiCall = useWatch({ control: form.control, name: apiCallPath })

  const displayName = apiCall?.name || t('unnamed')
  const pathWithQuery = formatPathWithQuery(apiCall?.path ?? '', apiCall?.queryParameters ?? [])
  const hasError = !!get(form.formState.errors, apiCallPath)
  const isRequestOpen = !collapsedSections.has('request')
  const isPaginationOpen = !collapsedSections.has('pagination')
  const isAnySectionOpen = API_CALL_SECTIONS.some(section => !collapsedSections.has(section))
  const toggleAllLabel = isAnySectionOpen ? t('collapseAll') : t('expandAll')
  const borderClassName = cn('rounded-md border py-3', hasError ? 'border-destructive' : 'border-transparent')

  const handleSectionOpenChange = (section: ApiCallSection, isOpen: boolean) => {
    const nextCollapsedSections = new Set(collapsedSections)
    if (isOpen) {
      nextCollapsedSections.delete(section)
    } else {
      nextCollapsedSections.add(section)
    }
    onCollapsedSectionsChange(nextCollapsedSections)
  }

  const handleToggleAll = () => onCollapsedSectionsChange(new Set(isAnySectionOpen ? API_CALL_SECTIONS : []))

  if (!isEditing) {
    return (
      <div className={cn('flex items-center gap-4 hover:bg-accent p-2', borderClassName)}>
        <button type="button" className="flex flex-1 items-center gap-12 text-left" onClick={onEdit}>
          <span className="text-sm font-semibold text-primary">{HTTP_METHODS.GET}</span>
          <span className="flex flex-col">
            <span className="font-semibold">{displayName}</span>
            <span className="font-mono text-sm text-muted-foreground">
              {pathWithQuery}
              {dataStructureName && ` → ${dataStructureName}`}
            </span>
          </span>
        </button>
        {!isDisabled && (
          <TableDropdownMenu menuItems={[{ label: tActions('delete'), icon: Trash2, onClick: onDelete }]} />
        )}
      </div>
    )
  }

  return (
    <div className={cn('-mx-2 flex flex-col gap-4 px-2', borderClassName)}>
      <div className="flex items-center justify-between">
        <span className="font-semibold">{displayName}</span>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button type="button" variant="outline" size="icon" aria-label={toggleAllLabel} onClick={handleToggleAll}>
              {isAnySectionOpen ? <ChevronsDownUp /> : <ChevronsUpDown />}
            </Button>
          </TooltipTrigger>
          <TooltipContent>{toggleAllLabel}</TooltipContent>
        </Tooltip>
      </div>

      <ApiCallRequest
        form={form}
        index={index}
        isOpen={isRequestOpen}
        isDisabled={isDisabled}
        onOpenChange={isOpen => handleSectionOpenChange('request', isOpen)}
      />

      <ApiCallPagination
        form={form}
        index={index}
        isOpen={isPaginationOpen}
        isDisabled={isDisabled}
        onOpenChange={isOpen => handleSectionOpenChange('pagination', isOpen)}
      />

      <div className="flex gap-2 mt-4">
        {isDisabled ? (
          <Button type="button" onClick={onClose}>
            {tActions('close')}
          </Button>
        ) : (
          <>
            <Button type="button" onClick={onClose}>
              {tActions('apply')}
            </Button>
            <Button type="button" variant="secondary" onClick={onCancel}>
              {tActions('cancel')}
            </Button>
          </>
        )}
      </div>
    </div>
  )
}
