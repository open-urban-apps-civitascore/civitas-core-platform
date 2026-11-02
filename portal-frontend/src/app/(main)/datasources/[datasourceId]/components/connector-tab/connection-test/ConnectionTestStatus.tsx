import { CircleAlertIcon, CircleCheckIcon, Info } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { ReactNode } from 'react'

import { Spinner } from '@/components/ui/spinner'
import { ConnectionTestProblem, ConnectionTestResult } from '@/types/connectionTest'
import { ConnectorType } from '@/types/connectors'
import { toPreviewText } from '@/utils/connectionTest'

const UNKNOWN_CAUSE = 'UNKNOWN'
const HTTP_OK = 200

const PROBLEM_CODES = [
  'VALIDATION_FAILED',
  'MALFORMED_REQUEST',
  'FORBIDDEN',
  'NOT_FOUND',
  'TOO_MANY_REQUESTS',
  'EXTERNAL_SYSTEM_ERROR',
  'EXTERNAL_SYSTEM_TIMEOUT',
]

const toProblemCause = (code: string) => (PROBLEM_CODES.includes(code) ? code : UNKNOWN_CAUSE)

interface ResultDetailsProps {
  result: ConnectionTestResult
  connectorType: ConnectorType
}

const ResultDetails = (props: ResultDetailsProps) => {
  const { result, connectorType } = props
  const t = useTranslations('datasources.connectorTab.connectionTest')
  const previewText = toPreviewText(result, connectorType)
  return (
    <>
      {result.httpStatus != null && result.httpStatus !== HTTP_OK && (
        <p>{t('httpStatus', { status: result.httpStatus })}</p>
      )}
      {previewText !== null && (
        <pre className="rounded-md border bg-muted p-4 font-mono text-sm whitespace-pre-wrap break-all max-h-80 overflow-auto">
          {previewText}
        </pre>
      )}
      {result.previewTruncated && <p className="text-xs text-muted-foreground">{t('previewTruncated')}</p>}
    </>
  )
}

interface FailureProps {
  cause: string
  details: string[]
  children?: ReactNode
}

const Failure = (props: FailureProps) => {
  const { cause, details, children } = props
  const t = useTranslations('datasources.connectorTab.connectionTest')
  return (
    <div className="flex items-start gap-2">
      <CircleAlertIcon className="size-5 shrink-0 text-destructive" />
      <div className="flex min-w-0 flex-col gap-2">
        <div className="text-destructive">
          <p className="font-medium">{t('failure')}</p>
          <p>
            {t('cause')} {t(`causes.${cause}`)}
          </p>
          {details.length > 0 && (
            <p>
              {t('details')} {details.join(' ')}
            </p>
          )}
        </div>
        {children}
      </div>
    </div>
  )
}

interface ConnectionTestStatusProps {
  connectorType: ConnectorType
  isPending: boolean
  isPasswordRequired: boolean
  result?: ConnectionTestResult
  problem?: ConnectionTestProblem
}

export const ConnectionTestStatus = (props: ConnectionTestStatusProps) => {
  const { connectorType, isPending, isPasswordRequired, result, problem } = props
  const t = useTranslations('datasources.connectorTab.connectionTest')

  if (isPending) {
    return (
      <div className="mt-4 flex items-center gap-2 text-sm">
        <Spinner className="size-6" />
        <span>{t('connecting')}</span>
      </div>
    )
  }

  if (isPasswordRequired) {
    return (
      <div className="mt-4 flex items-center gap-2 text-sm">
        <Info className="size-4 shrink-0" />
        <span>{t('reenterPassword')}</span>
      </div>
    )
  }

  if (result?.success) {
    return (
      <div className="mt-4 flex items-start gap-2 text-sm">
        <CircleCheckIcon className="size-5 shrink-0 fill-green-600 text-white" />
        <div className="flex min-w-0 flex-col gap-2">
          <p className="font-medium">{t('success')}</p>
          <ResultDetails result={result} connectorType={connectorType} />
        </div>
      </div>
    )
  }

  if (result) {
    return (
      <div className="mt-4 text-sm">
        <Failure cause={result.reason ?? UNKNOWN_CAUSE} details={result.message ? [result.message] : []}>
          <ResultDetails result={result} connectorType={connectorType} />
        </Failure>
      </div>
    )
  }

  if (problem) {
    return (
      <div className="mt-4 text-sm">
        <Failure cause={toProblemCause(problem.code)} details={problem.details} />
      </div>
    )
  }

  return null
}
