import { useTranslations } from 'next-intl'
import { useMemo, useState } from 'react'
import { UseFormReturn, useWatch } from 'react-hook-form'

import { useTestDatasourceConnection } from '@/app/services/api/datasources/clientRequests'
import { Button } from '@/components/ui/button'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { DatasourceFormDraft } from '@/types/datasources'
import { hasMaskedSecret, toConnectionTestInput } from '@/utils/connectionTest'
import { toConnectionTestProblem } from '@/utils/errors'

import { ConnectionTestStatus } from './ConnectionTestStatus'

type TestedRun = { key: string; kind: 'test' | 'passwordRequired' }

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
  const mutation = useTestDatasourceConnection()
  const [testedRun, setTestedRun] = useState<TestedRun | null>(null)

  const connectorType = useWatch({ control: form.control, name: 'connectorType' })
  const configuration = useWatch({ control: form.control, name: 'configuration' })
  const currentInput = useMemo(
    () => toConnectionTestInput({ connectorType, configuration }),
    [connectorType, configuration],
  )
  const currentKey = JSON.stringify(currentInput)
  const canTest = isConnectorComplete && currentInput !== null
  const isRunCurrent = testedRun?.key === currentKey
  const isTestCurrent = isRunCurrent && testedRun.kind === 'test'

  const runTest = () => {
    const values = form.getValues()
    if (isConnectorChanged && hasMaskedSecret(values)) {
      setTestedRun({ key: currentKey, kind: 'passwordRequired' })
      return
    }
    const input = toConnectionTestInput(values)
    if (!input) return
    setTestedRun({ key: currentKey, kind: 'test' })
    mutation.mutate({ id: datasourceId, ...input })
  }

  if (!connectorType) return null

  return (
    <div className="border-t mt-3 pt-6">
      <div className={cn(isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,1fr)]')}>
        <span className="flex h-9 items-center self-start text-sm leading-none font-medium">{t('title')}</span>
        <div>
          <div className="flex flex-wrap items-center gap-4">
            <Button type="button" variant="secondary" disabled={!canTest || mutation.isPending} onClick={runTest}>
              {isRunCurrent && !mutation.isPending ? t('testAgain') : t('button')}
            </Button>
            {!canTest && <span className="text-sm text-muted-foreground">{t('incompleteHint')}</span>}
          </div>
          <ConnectionTestStatus
            connectorType={connectorType}
            isPending={mutation.isPending}
            isPasswordRequired={isRunCurrent && testedRun.kind === 'passwordRequired'}
            result={isTestCurrent ? mutation.data?.data : undefined}
            problem={isTestCurrent && mutation.isError ? toConnectionTestProblem(mutation.error) : undefined}
          />
        </div>
      </div>
    </div>
  )
}
