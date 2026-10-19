import { ChevronDown, Copy, Trash2 } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { get, useFieldArray, UseFormReturn, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { Button } from '@/components/ui/button'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { InputGroup, InputGroupInput } from '@/components/ui/input-group'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { HTTP_METHODS } from '@/const/connectors'
import { cn } from '@/lib/utils'
import { HttpApiCallsForm } from '@/types/connectors'
import { buildApiCallUrl, formatPathWithQuery } from '@/utils/connectors'

import { ApiCallParameterRows } from './ApiCallParameterRows'

const EMPTY_PARAMETER = { name: '', value: '' }

interface ApiCallItemProps {
  form: UseFormReturn<HttpApiCallsForm>
  index: number
  isEditing: boolean
  isRequestOpen: boolean
  isDisabled: boolean
  dataStructureName?: string
  onEdit: () => void
  onRequestOpenChange: (isRequestOpen: boolean) => void
  onDelete: () => void
  onClose: () => void
  onCancel: () => void
}

export const ApiCallItem = (props: ApiCallItemProps) => {
  const {
    form,
    index,
    isEditing,
    isRequestOpen,
    isDisabled,
    dataStructureName,
    onEdit,
    onRequestOpenChange,
    onDelete,
    onClose,
    onCancel,
  } = props
  const t = useTranslations('datasources.apiCallsTab')
  const tActions = useTranslations('common.actions')

  const apiCallPath = `configuration.apiCalls.${index}` as const
  const apiCall = useWatch({ control: form.control, name: apiCallPath })
  const baseUrl = useWatch({ control: form.control, name: 'configuration.baseUrl' })
  const queryParameters = useFieldArray({ control: form.control, name: `${apiCallPath}.queryParameters` })
  const headers = useFieldArray({ control: form.control, name: `${apiCallPath}.headers` })

  const displayName = apiCall?.name || t('unnamed')
  const pathWithQuery = formatPathWithQuery(apiCall?.path ?? '', apiCall?.queryParameters ?? [])
  const hasParameters = queryParameters.fields.length > 0 || headers.fields.length > 0
  const hasError = !!get(form.formState.errors, apiCallPath)
  const borderClassName = cn('rounded-md border py-3', hasError ? 'border-destructive' : 'border-transparent')

  const handleCopyUrl = async () => {
    try {
      await navigator.clipboard.writeText(
        buildApiCallUrl(baseUrl ?? '', apiCall?.path ?? '', apiCall?.queryParameters ?? []),
      )
      toast.success(t('copySuccess'))
    } catch {
      // Clipboard access can fail in insecure contexts.
    }
  }

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
    <div className={cn('flex flex-col gap-4', borderClassName)}>
      <span className="font-semibold">{displayName}</span>

      <Collapsible open={isRequestOpen} onOpenChange={onRequestOpenChange} className="group/request">
        <div className="group/request-row flex items-center">
          <CollapsibleTrigger asChild>
            <button type="button" className="flex items-center gap-6 text-left">
              <span className="text-sm font-semibold uppercase tracking-wide text-primary">{t('request.title')}</span>
              <span data-endpoint className="font-mono text-sm text-muted-foreground">
                {HTTP_METHODS.GET} {pathWithQuery}
              </span>
            </button>
          </CollapsibleTrigger>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label={t('copyUrl')}
                className="ml-2 size-8 text-muted-foreground opacity-0 hover:opacity-100 focus-visible:opacity-100 group-has-[[data-endpoint]:hover]/request-row:opacity-100"
                onClick={handleCopyUrl}
              >
                <Copy />
              </Button>
            </TooltipTrigger>
            <TooltipContent>{t('copyUrl')}</TooltipContent>
          </Tooltip>
          <CollapsibleTrigger asChild>
            <button type="button" tabIndex={-1} aria-hidden className="flex flex-1 justify-end self-stretch">
              <ChevronDown className="size-4 group-data-[state=open]/request:rotate-180" />
            </button>
          </CollapsibleTrigger>
        </div>

        <CollapsibleContent className="flex flex-col gap-4 pt-4">
          <div className="flex flex-col gap-2 mb-2">
            <p className="font-semibold">
              {t('request.question')}
              <span className="text-red-500 ml-1">*</span>
            </p>
            <p className="text-sm text-muted-foreground">{t('path.hint')}</p>
          </div>

          <FormField
            control={form.control}
            name={`${apiCallPath}.path`}
            render={({ field }) => (
              <FormItem>
                <InputGroup className="h-12">
                  <Select value={HTTP_METHODS.GET} disabled>
                    <SelectTrigger
                      aria-label={t('method.label')}
                      className="w-28 rounded-r-none border-0 bg-muted text-primary shadow-none disabled:opacity-100 data-[size=default]:h-full"
                    >
                      <SelectValue>{HTTP_METHODS.GET}</SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={HTTP_METHODS.GET}>{HTTP_METHODS.GET}</SelectItem>
                    </SelectContent>
                  </Select>
                  <FormControl>
                    <InputGroupInput
                      {...field}
                      aria-label={t('path.label')}
                      className="h-full font-mono"
                      placeholder={isDisabled ? undefined : '/stations'}
                      disabled={isDisabled}
                    />
                  </FormControl>
                </InputGroup>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name={`${apiCallPath}.name`}
            render={({ field }) => (
              <FormItem className="flex flex-col gap-2">
                <FormLabel>
                  {t('name.label')}
                  <span className="text-red-500 ml-1">*</span>
                </FormLabel>
                <FormControl>
                  <Input
                    {...field}
                    className="h-12"
                    placeholder={isDisabled ? undefined : t('name.placeholder')}
                    disabled={isDisabled}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {hasParameters && (
            <div className="flex flex-col gap-3">
              <ApiCallParameterRows
                form={form}
                name={`${apiCallPath}.queryParameters`}
                fields={queryParameters.fields}
                typeLabel={t('parameters.query')}
                isDisabled={isDisabled}
                onRemove={queryParameters.remove}
              />
              <ApiCallParameterRows
                form={form}
                name={`${apiCallPath}.headers`}
                fields={headers.fields}
                typeLabel={t('parameters.header')}
                isDisabled={isDisabled}
                onRemove={headers.remove}
              />
            </div>
          )}

          {!isDisabled && (
            <div className="flex gap-4">
              <Button
                type="button"
                variant="link"
                className="px-0"
                onClick={() => queryParameters.append(EMPTY_PARAMETER)}
              >
                {t('parameters.addQuery')}
              </Button>
              <Button type="button" variant="link" className="px-0" onClick={() => headers.append(EMPTY_PARAMETER)}>
                {t('parameters.addHeader')}
              </Button>
            </div>
          )}
        </CollapsibleContent>
      </Collapsible>

      <div className={cn('flex gap-2', isDisabled && 'mt-4')}>
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
