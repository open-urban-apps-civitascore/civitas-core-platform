import { StatusBadge } from '@/components/status/status-badge/StatusBadge'
import { DATASTRUCTURE_STATUS_TYPES, DatastructureStatusType } from '@/types/datastructures'

interface VersionStatusCountsProps {
  versionCountByStatus?: Record<DatastructureStatusType, number>
}

export const VersionStatusCounts = (props: VersionStatusCountsProps) => {
  const { versionCountByStatus } = props
  const availableCount = versionCountByStatus?.[DATASTRUCTURE_STATUS_TYPES.AVAILABLE] ?? 0
  const draftCount = versionCountByStatus?.[DATASTRUCTURE_STATUS_TYPES.DRAFT] ?? 0

  if (!availableCount && !draftCount) return null

  return (
    <div className="flex items-center gap-2">
      {availableCount > 0 && <StatusBadge status={DATASTRUCTURE_STATUS_TYPES.AVAILABLE} count={availableCount} />}
      {draftCount > 0 && <StatusBadge status={DATASTRUCTURE_STATUS_TYPES.DRAFT} count={draftCount} />}
    </div>
  )
}
