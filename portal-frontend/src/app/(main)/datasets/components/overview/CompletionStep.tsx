import { Circle, CircleCheckBig } from 'lucide-react'
import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { CompletionStepData } from '@/types/datasets'

interface CompletionStepProps {
  step: CompletionStepData
  datasetId: string
  // eslint-disable-next-line react/boolean-prop-naming
  disabled?: boolean
  className?: string
}

export const CompletionStep = (props: CompletionStepProps) => {
  const { step, datasetId, disabled = false, className } = props
  const t = useTranslations('datasets')
  const searchParams = useSearchParams()

  const ButtonLink = ({ button }: { button: 'button1' | 'button2' }) =>
    disabled ? (
      <Button variant="outline" disabled>
        {t(`overview.completion.${step.key}.${button}`)}
      </Button>
    ) : (
      <Button asChild variant="outline">
        <Link href={`/datasets/${datasetId}/${step.key}?${searchParams.toString()}`}>
          {t(`overview.completion.${step.key}.${button}`)}
        </Link>
      </Button>
    )
  return (
    <div>
      <div className={cn('flex justify-between items-center ', className)}>
        <div className="w-full items-center flex gap-2">
          {step.isCompleted ? <CircleCheckBig /> : <Circle />}
          <div className="flex-1">
            <h3 className="text-2xl font-bold">{t(`overview.completion.${step.key}.title`)}</h3>
          </div>
        </div>
        <div className="flex gap-10">
          <ButtonLink button="button1" />
          {step.buttons === 2 && <ButtonLink button="button2" />}
        </div>
      </div>
      {step.content && <div className="font-semibold mt-6 ml-8.5">{step.content}</div>}
    </div>
  )
}
