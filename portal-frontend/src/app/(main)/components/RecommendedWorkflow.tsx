'use client'

import { useTranslations } from 'next-intl'

import { WorkflowStepCard } from './WorkflowStepCard'

const WORKFLOW_STEPS = [
  {
    key: 'datastructure',
    illustrationSrc: '/svg/workflow/datastructure.svg',
    illustrationWidth: 129,
    illustrationHeight: 40,
    richTitle: false,
  },
  {
    key: 'datasource',
    illustrationSrc: '/svg/workflow/datasource.svg',
    illustrationWidth: 149,
    illustrationHeight: 98,
    richTitle: false,
  },
  {
    key: 'pipeline',
    illustrationSrc: '/svg/workflow/pipeline.svg',
    illustrationWidth: 543,
    illustrationHeight: 156,
    richTitle: true,
  },
] as const

export const RecommendedWorkflow = () => {
  const t = useTranslations('welcome.recommendedWorkflow')

  return (
    <section className="flex w-full flex-col items-start gap-6 p-8">
      <h2 className="w-full text-center text-lg font-bold sm:text-xl">{t('title')}</h2>
      <div className="flex w-full flex-col items-start justify-center gap-6 lg:flex-row lg:items-stretch lg:gap-16">
        {WORKFLOW_STEPS.map((step, index) => (
          <WorkflowStepCard
            key={step.key}
            className="lg:flex-none"
            stepNumber={index + 1}
            title={
              step.richTitle
                ? t.rich(`steps.${step.key}.title`, { b: chunks => <span className="font-semibold">{chunks}</span> })
                : t(`steps.${step.key}.title`)
            }
            subtitle={t(`steps.${step.key}.subtitle`)}
            illustrationSrc={step.illustrationSrc}
            illustrationWidth={step.illustrationWidth}
            illustrationHeight={step.illustrationHeight}
          />
        ))}
      </div>
    </section>
  )
}
