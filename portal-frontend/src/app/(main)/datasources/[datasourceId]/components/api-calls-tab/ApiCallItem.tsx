import { ChevronDown, Trash2 } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { get, useFieldArray, UseFormReturn, useWatch } from 'react-hook-form'

import { TableDropdownMenu } from '@/components/dropdown-menu/TableDropdownMenu'
import { Button } from '@/components/ui/button'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { InputGroup, InputGroupInput } from '@/components/ui/input-group'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { HTTP_METHODS } from '@/const/connectors'
import { cn } from '@/lib/utils'
import { HttpApiCallsForm } from '@/types/connectors'
import { formatPathWithQuery } from '@/utils/connectors'

import { ApiCallParameterRows } from './ApiCallParameterRows'

const EMPTY_PARAMETER = { name: '', value: '' }

interface ApiCallItemProps {
  form: UseFormReturn<HttpApiCallsForm>
  index: number
  isOpen: boolean
  isDisabled: boolean
  dataStructureName?: string
  onOpenChange: (isOpen: boolean) => void
  onDelete: () => void
}

export const ApiCallItem = (props: ApiCallItemProps) => {
  const { form, index, isOpen, isDisabled, dataStructureName, onOpenChange, onDelete } = props
  const t = useTranslations('datasources.apiCallsTab')
  const tActions = useTranslations('common.actions')

  const apiCallPath = `configuration.apiCalls.${index}` as const
  const apiCall = useWatch({ control: form.control, name: apiCallPath })
  const queryParameters = useFieldArray({ control: form.control, name: `${apiCallPath}.queryParameters` })
  const headers = useFieldArray({ control: form.control, name: `${apiCallPath}.headers` })

  const displayName = apiCall?.name || t('unnamed')
  const pathWithQuery = formatPathWithQuery(apiCall?.path ?? '', apiCall?.queryParameters ?? [])
  const hasError = !!get(form.formState.errors, apiCallPath)

  return (
    <Collapsible
      open={isOpen}
      onOpenChange={onOpenChange}
      className={cn('group/api-call rounded-md border py-3', hasError ? 'border-destructive' : 'border-transparent')}
    >
      <div className="flex items-center gap-4 rounded-md hover:bg-accent group-data-[state=open]/api-call:hidden p-2">
        <CollapsibleTrigger asChild>
          <button type="button" className="flex flex-1 items-center gap-12 text-left">
            <span className="text-sm font-semibold text-primary">{HTTP_METHODS.GET}</span>
            <span className="flex flex-col">
              <span className="font-semibold">{displayName}</span>
              <span className="font-mono text-sm text-muted-foreground">
                {pathWithQuery}
                {dataStructureName && ` → ${dataStructureName}`}
              </span>
            </span>
          </button>
        </CollapsibleTrigger>
        {!isDisabled && (
          <TableDropdownMenu menuItems={[{ label: tActions('delete'), icon: Trash2, onClick: onDelete }]} />
        )}
      </div>

      <CollapsibleContent className="flex flex-col gap-4">
        <CollapsibleTrigger asChild>
          <button type="button" className="font-semibold text-left">
            {displayName}
          </button>
        </CollapsibleTrigger>

        <Collapsible defaultOpen className="group/request">
          <CollapsibleTrigger asChild>
            <button type="button" className="flex w-full items-center gap-6 text-left">
              <span className="text-sm font-semibold uppercase tracking-wide text-primary">{t('request.title')}</span>
              <span className="flex-1 font-mono text-sm text-muted-foreground">
                {HTTP_METHODS.GET} {pathWithQuery}
              </span>
              <ChevronDown className="size-4 group-data-[state=open]/request:rotate-180" />
            </button>
          </CollapsibleTrigger>

          <CollapsibleContent className="flex flex-col gap-4 pt-4">
            <div className="flex flex-col gap-2 mb-2">
              <p className="font-semibold">{t('request.question')}</p>
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
      </CollapsibleContent>
    </Collapsible>
  )
}
