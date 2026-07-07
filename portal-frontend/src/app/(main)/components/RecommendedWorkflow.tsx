'use client'

import { useTranslations } from 'next-intl'

import { WorkflowStepCard } from './WorkflowStepCard'

export const RecommendedWorkflow = () => {
  const t = useTranslations('welcome.recommendedWorkflow')

  return (
    <section className="flex w-full flex-col items-start gap-6 p-8">
      <h2 className="w-full text-center text-lg font-bold sm:text-xl">{t('title')}</h2>
      <div className="flex w-full flex-col items-start justify-center gap-6 lg:flex-row lg:items-stretch lg:gap-16">
        <WorkflowStepCard
          className="lg:flex-none"
          stepNumber={1}
          title={t('steps.datastructure.title')}
          subtitle={t('steps.datastructure.subtitle')}
          illustrationSrc="/svg/workflow/datastructure.svg"
          illustrationWidth={129}
          illustrationHeight={40}
        />
        <WorkflowStepCard
          className="lg:flex-none"
          stepNumber={2}
          title={t('steps.datasource.title')}
          subtitle={t('steps.datasource.subtitle')}
          illustrationSrc="/svg/workflow/datasource.svg"
          illustrationWidth={149}
          illustrationHeight={98}
        />
        <WorkflowStepCard
          className="lg:flex-none"
          stepNumber={3}
          title={t.rich('steps.pipeline.title', {
            b: chunks => <span className="font-semibold">{chunks}</span>,
          })}
          subtitle={t('steps.pipeline.subtitle')}
          illustrationSrc="/svg/workflow/pipeline.svg"
          illustrationWidth={543}
          illustrationHeight={156}
        />
      </div>
    </section>
  )
}
