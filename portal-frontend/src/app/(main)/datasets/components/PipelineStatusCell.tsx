import { useTranslations } from 'next-intl'

import { ActivityBadge } from '@/components/activity-badge/ActivityBadge'
import { Badge } from '@/components/ui/badge'
import { DATASET_STATUS_TYPES, DatasetStatusTypes, PipelineBasicInfo, PipelineRuntimeStatus } from '@/types/datasets'

type RuntimeState = PipelineRuntimeStatus['state']
type LabelKey = 'active' | 'error' | 'stopped'

const STATE_BADGES: Record<RuntimeState, { isActive: boolean; labelKey: LabelKey }> = {
  OK: { isActive: true, labelKey: 'active' },
  ERROR: { isActive: false, labelKey: 'error' },
  STOPPED: { isActive: false, labelKey: 'stopped' },
}

interface PipelineStatusCellProps {
  status: DatasetStatusTypes
  pipelines: PipelineBasicInfo[]
}

export const PipelineStatusCell = (props: PipelineStatusCellProps) => {
  const { status, pipelines } = props
  const t = useTranslations('datasets.pipelineStatus')

  if (status !== DATASET_STATUS_TYPES.AVAILABLE) {
    return <Badge variant="secondary">{pipelines.length}</Badge>
  }

  const shouldShowCount = pipelines.length > 1
  const label = (count: number, key: LabelKey) => (shouldShowCount ? `${count} ${t(key)}` : t(key))

  const states = Object.keys(STATE_BADGES) as RuntimeState[]
  const stateCounts = states.map(state => ({
    state,
    count: pipelines.filter(p => p.runtimeStatus?.state === state).length,
  }))
  const badges = stateCounts.filter(entry => entry.count > 0)

  if (badges.length === 0) {
    return <span>-</span>
  }

  return (
    <div className="flex items-center gap-1">
      {badges.map(({ state, count }) => (
        <ActivityBadge
          key={state}
          isActive={STATE_BADGES[state].isActive}
          title={label(count, STATE_BADGES[state].labelKey)}
        />
      ))}
    </div>
  )
}
