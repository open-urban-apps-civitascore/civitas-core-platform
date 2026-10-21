import { ChevronDown, Copy } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useFieldArray, UseFormReturn, useWatch } from 'react-hook-form'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { InputGroup, InputGroupInput } from '@/components/ui/input-group'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { HTTP_METHODS } from '@/const/connectors'
import { HttpApiCallsForm } from '@/types/connectors'
import { buildApiCallUrl, formatPathWithQuery } from '@/utils/connectors'

import { ApiCallParameterRows } from './ApiCallParameterRows'

const EMPTY_PARAMETER = { name: '', value: '' }

interface ApiCallRequestProps {
  form: UseFormReturn<HttpApiCallsForm>
  index: number
  isOpen: boolean
  isDisabled: boolean
  onOpenChange: (isOpen: boolean) => void
}

export const ApiCallRequest = (props: ApiCallRequestProps) => {
  const { form, index, isOpen, isDisabled, onOpenChange } = props
  const t = useTranslations('datasources.apiCallsTab')

  const apiCallPath = `configuration.apiCalls.${index}` as const
  const apiCall = useWatch({ control: form.control, name: apiCallPath })
  const baseUrl = useWatch({ control: form.control, name: 'configuration.baseUrl' })
  const queryParameters = useFieldArray({ control: form.control, name: `${apiCallPath}.queryParameters` })
  const headers = useFieldArray({ control: form.control, name: `${apiCallPath}.headers` })

  const pathWithQuery = formatPathWithQuery(apiCall?.path ?? '', apiCall?.queryParameters ?? [])
  const hasParameters = queryParameters.fields.length > 0 || headers.fields.length > 0

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

  return (
    <Collapsible open={isOpen} onOpenChange={onOpenChange} className="group/request">
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
  )
}
