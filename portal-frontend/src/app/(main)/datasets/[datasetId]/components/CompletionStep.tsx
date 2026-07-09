import { CircleCheckBig, CircleDashed } from 'lucide-react'
import { useSearchParams } from 'next/navigation'

import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { CompletionStepData, CompletionStepParam } from '@/types/datasets'

interface CompletionStepProps {
  step: CompletionStepData
  datasetId: string
  // eslint-disable-next-line react/boolean-prop-naming
  disabled?: boolean
  className?: string
}

export const CompletionStep = (props: CompletionStepProps) => {
  const { step, datasetId, disabled = false, className } = props
  const searchParams = useSearchParams()

  const buildHref = (routeParam: CompletionStepParam, queryParam?: string) => {
    const route = routeParam === 'data-flow' ? 'data-flow/pipeline-editor' : routeParam
    const basePath = `/datasets/${datasetId}/${route}`

    const searchParamsString = searchParams?.toString()

    if (queryParam && searchParamsString) {
      return `${basePath}?${queryParam}&${searchParamsString}`
    }
    if (queryParam) {
      return `${basePath}?${queryParam}`
    }
    if (searchParamsString) {
      return `${basePath}?${searchParamsString}`
    }
    return basePath
  }

  const ButtonLink = ({
    buttonText,
    routeParam,
    queryParam,
  }: {
    buttonText: string
    routeParam: CompletionStepParam
    queryParam?: string
  }) =>
    disabled ? (
      <Button variant="outline" disabled>
        {buttonText}
      </Button>
    ) : (
      <Button asChild variant="outline">
        <GuardedLink href={buildHref(routeParam, queryParam)}>{buttonText}</GuardedLink>
      </Button>
    )
  return (
    <div data-testid="completionStep">
      <div className={cn('flex justify-between items-center ', className)}>
        <div className="w-full flex flex-col gap-1">
          <div className="flex items-center gap-2">
            {step.isCompleted ? (
              <CircleCheckBig data-testid="circleCheck" className="w-6 h-6 text-green-600" />
            ) : (
              <CircleDashed data-testid="circle" className="w-6 h-6 text-muted-foreground" />
            )}
            <h3 className="text-xl font-semibold">{step.title}</h3>
          </div>
          {step.description && <p className="text-sm text-muted-foreground ml-8">{step.description}</p>}
        </div>
        <div className="flex gap-10">
          {step.actionElement
            ? step.actionElement
            : step.buttons.map(button => (
                <ButtonLink
                  key={button.text}
                  buttonText={button.text}
                  routeParam={button.routeParam}
                  queryParam={button.queryParam}
                />
              ))}
        </div>
      </div>
      {step.description && <div className="border-t mt-4" />}
      {step.content && (
        <div data-testid="completionStepContent" className="font-semibold mt-6 ml-8.5">
          {step.content}
        </div>
      )}
    </div>
  )
}
