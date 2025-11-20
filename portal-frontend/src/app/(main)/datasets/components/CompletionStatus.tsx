import { CheckedState } from '@radix-ui/react-checkbox'
import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'

import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'

type CompletionStepKey = 'metadata' | 'groups' | 'configuration' | 'distribution' | 'permissions' | 'publication'

type CompletionStepData = {
  key: CompletionStepKey
  status: CheckedState
}

const completionSteps: Record<CompletionStepKey, CompletionStepData> = {
  metadata: { key: 'metadata', status: false },
  groups: { key: 'groups', status: false },
  configuration: { key: 'configuration', status: false },
  distribution: { key: 'distribution', status: false },
  permissions: { key: 'permissions', status: false },
  publication: { key: 'publication', status: false },
}

interface CompletionStepProps {
  step: CompletionStepData
  datasetId: string
  className?: string
  // eslint-disable-next-line react/boolean-prop-naming
  disabled: boolean
}

interface DatasetCompletionStatusProps {
  datasetId: string
  // eslint-disable-next-line react/boolean-prop-naming
  disabled: boolean
}

const CompletionStep = (props: CompletionStepProps) => {
  const { step, datasetId, disabled, className } = props
  const t = useTranslations('datasets')
  const searchParams = useSearchParams()
  return (
    <div className={cn('flex justify-between items-center', className)}>
      <Button asChild size="sm" className="rounded-xl">
        <Link
          href={`/datasets/${datasetId}/${step.key}?${searchParams.toString()}`}
          className={cn(disabled && 'pointer-events-none opacity-50')}
        >
          {t(`overview.completion.${step.key}`)}
        </Link>
      </Button>
      <Checkbox checked={step.status} />
    </div>
  )
}

export const DatasetCompletionStatus = (props: DatasetCompletionStatusProps) => {
  const { datasetId, disabled } = props
  const t = useTranslations('datasets')
  const isMobile = useIsMobile()

  return (
    <div className={cn(isMobile && 'mt-6')}>
      <DetailsFieldContainer>
        <SubHeader title={t('overview.completion.title')} titleClassName={cn(disabled && 'text-muted-foreground')} />
      </DetailsFieldContainer>
      <DetailsFieldContainer>
        <CompletionStep step={completionSteps.metadata} datasetId={datasetId} disabled={disabled} className="mb-4" />
        <CompletionStep step={completionSteps.groups} datasetId={datasetId} disabled={disabled} />
      </DetailsFieldContainer>
      <DetailsFieldContainer>
        <CompletionStep
          step={completionSteps.configuration}
          datasetId={datasetId}
          disabled={disabled}
          className="mb-4"
        />
        <CompletionStep step={completionSteps.distribution} datasetId={datasetId} disabled={disabled} />
      </DetailsFieldContainer>
      <DetailsFieldContainer>
        <CompletionStep step={completionSteps.permissions} datasetId={datasetId} disabled={disabled} />
      </DetailsFieldContainer>
      <DetailsFieldContainer>
        <CompletionStep step={completionSteps.publication} datasetId={datasetId} disabled={disabled} />
      </DetailsFieldContainer>
    </div>
  )
}
