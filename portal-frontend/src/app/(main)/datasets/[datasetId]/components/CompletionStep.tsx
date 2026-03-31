import { Circle, CircleCheckBig } from 'lucide-react'
import { useSearchParams } from 'next/navigation'

import { GuardedLink } from '@/components/appSidebar/components/GuardedLink'
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
        <div className="w-full items-center flex gap-2">
          {step.isCompleted ? <CircleCheckBig data-testid="circleCheck" /> : <Circle data-testid="circle" />}
          <div className="flex-1">
            <h3 className="text-2xl font-bold">{step.title}</h3>
          </div>
        </div>
        <div className="flex gap-10">
          {step.buttons.map(button => (
            <ButtonLink
              key={button.text}
              buttonText={button.text}
              routeParam={button.routeParam}
              queryParam={button.queryParam}
            />
          ))}
        </div>
      </div>
      {step.content && (
        <div data-testid="completionStepContent" className="font-semibold mt-6 ml-8.5">
          {step.content}
        </div>
      )}
    </div>
  )
}
