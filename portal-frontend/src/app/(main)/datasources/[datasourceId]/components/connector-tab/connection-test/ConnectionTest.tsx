import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { UseFormReturn, useWatch } from 'react-hook-form'

import {
  useClearConnectionTestRuns,
  useLastConnectionTestRun,
  useTestDatasourceConnection,
} from '@/app/services/api/datasources/clientRequests'
import { Button } from '@/components/ui/button'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { DatasourceFormDraft } from '@/types/datasources'
import { hasMaskedSecret, toConnectionTestInput } from '@/utils/connectionTest'
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
  const clearRuns = useClearConnectionTestRuns(datasourceId)
  const [passwordRequiredKey, setPasswordRequiredKey] = useState<string | null>(null)

  const connectorType = useWatch({ control: form.control, name: 'connectorType' })
  const configuration = useWatch({ control: form.control, name: 'configuration' })
  const currentInput = useMemo(
    () => toConnectionTestInput({ connectorType, configuration }),
    [connectorType, configuration],
  )
  const currentKey = JSON.stringify(currentInput)
  const canTest = isConnectorComplete && currentInput !== null
  const isPending = lastRun?.status === 'pending'
  const lastRunKey = lastRun ? JSON.stringify(lastRun.variables) : null
  const isPasswordRequired = passwordRequiredKey === currentKey
  const isTestCurrent = lastRunKey === currentKey
  const isRunCurrent = isTestCurrent || isPasswordRequired

  useEffect(() => {
    if (lastRunKey !== null && lastRunKey !== currentKey && !isPending) clearRuns()
  }, [lastRunKey, currentKey, isPending, clearRuns])

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
    mutation.mutate(input)
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
                {isConnectorComplete ? t('invalidHint') : t('incompleteHint')}
              </span>
            )}
          </div>
          <ConnectionTestStatus
            connectorType={connectorType}
            isPending={isPending}
            isPasswordRequired={isPasswordRequired}
            result={isTestCurrent && lastRun?.status === 'success' ? lastRun.data?.data : undefined}
            problem={isTestCurrent && lastRun?.status === 'error' ? toConnectionTestProblem(lastRun.error) : undefined}
          />
        </div>
      </div>
    </div>
  )
}
