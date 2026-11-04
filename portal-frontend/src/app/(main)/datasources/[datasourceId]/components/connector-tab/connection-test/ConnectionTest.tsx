import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { UseFormReturn, useWatch } from 'react-hook-form'

import {
  useDiscardConnectionTestRuns,
  useLastConnectionTestRun,
  useTestDatasourceConnection,
} from '@/app/services/api/datasources/clientRequests'
import { Button } from '@/components/ui/button'
import { CONNECTOR_TYPES } from '@/const/connectors'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { DatasourceFormDraft } from '@/types/datasources'
import { hasMaskedSecret, isApiCallIncomplete, toConnectionTestInput } from '@/utils/connectionTest'
import { toConnectionTestProblem } from '@/utils/errors'

import { ConnectionTestStatus } from './ConnectionTestStatus'

interface ConnectionTestProps {
  form: UseFormReturn<DatasourceFormDraft>
  datasourceId: string
  isConnectorComplete: boolean
  isConnectorChanged: boolean
}

export const ConnectionTest = (props: ConnectionTestProps) => {
  const { form, datasourceId, isConnectorComplete, isConnectorChanged } = props
  const t = useTranslations('datasources.connectorTab.connectionTest')
  const isMobile = useIsMobile()
  const mutation = useTestDatasourceConnection(datasourceId)
  const lastRun = useLastConnectionTestRun(datasourceId)
  const discardRuns = useDiscardConnectionTestRuns(datasourceId)
  const [passwordRequiredKey, setPasswordRequiredKey] = useState<string | null>(null)

  const connectorType = useWatch({ control: form.control, name: 'connectorType' })
  const configuration = useWatch({ control: form.control, name: 'configuration' })
  const currentInput = useMemo(
    () => toConnectionTestInput({ connectorType, configuration }),
    [connectorType, configuration],
  )
  const currentKey = JSON.stringify(currentInput)
  const canTest = isConnectorComplete && currentInput !== null
  const isIncomplete = !isConnectorComplete || isApiCallIncomplete({ connectorType, configuration })
  const incompleteHint = connectorType === CONNECTOR_TYPES.HTTP ? 'httpIncompleteHint' : 'incompleteHint'
  const isPending = lastRun?.status === 'pending'
  const lastRunVariables = lastRun?.variables
  const lastRunKey = lastRunVariables ? JSON.stringify(lastRunVariables.input) : null
  const isOutdated = !!lastRunVariables?.marker.isOutdated
  const isRunOutdated = isOutdated || lastRunKey !== currentKey
  const isPasswordRequired = passwordRequiredKey === currentKey
  const isTestCurrent = lastRunKey === currentKey && !isOutdated
  const isRunCurrent = isTestCurrent || isPasswordRequired

  useEffect(() => {
    if (lastRunKey !== null && (lastRunKey !== currentKey || isOutdated)) discardRuns()
  }, [lastRunKey, currentKey, isOutdated, isPending, discardRuns])

  if (passwordRequiredKey !== null && !isPasswordRequired) setPasswordRequiredKey(null)

  const runTest = () => {
    const values = form.getValues()
    if (isConnectorChanged && hasMaskedSecret(values)) {
      setPasswordRequiredKey(currentKey)
      return
    }
    const input = toConnectionTestInput(values)
    if (!input) return
    setPasswordRequiredKey(null)
    mutation.mutate({ input, marker: { isOutdated: false } })
  }

  if (!connectorType) return null

  return (
    <div className="border-t mt-3 pt-6">
      <div className={cn(isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,1fr)]')}>
        <span className="flex h-9 items-center self-start text-sm leading-none font-medium">{t('title')}</span>
        <div>
          <div className="flex flex-wrap items-center gap-4">
            <Button type="button" variant="secondary" disabled={!canTest || isPending} onClick={runTest}>
              {isRunCurrent && !isPending ? t('testAgain') : t('button')}
            </Button>
            {!canTest && (
              <span className="text-sm text-muted-foreground">
                {isIncomplete ? t(incompleteHint) : t('invalidHint')}
              </span>
            )}
          </div>
          <ConnectionTestStatus
            connectorType={connectorType}
            isPending={isPending && !isRunOutdated}
            isOutdatedPending={isPending && isRunOutdated}
            isPasswordRequired={isPasswordRequired}
            result={isTestCurrent && lastRun?.status === 'success' ? lastRun.data?.data : undefined}
            problem={isTestCurrent && lastRun?.status === 'error' ? toConnectionTestProblem(lastRun.error) : undefined}
          />
        </div>
      </div>
    </div>
  )
}
