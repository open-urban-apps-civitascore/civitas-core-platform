import { useTranslations } from 'next-intl'

import { StatusBadge } from '@/components/status/status-badge/StatusBadge'
import { DatastructureVersion } from '@/types/datastructures'

interface DatastructureVersionInfoProps {
  version: DatastructureVersion
}

export const DatastructureVersionInfo = (props: DatastructureVersionInfoProps) => {
  const { version } = props
  const t = useTranslations('datasources.dataModel.versionInfo')
  return (
    <div
      className="flex min-w-0 items-center gap-6 text-sm font-semibold text-foreground"
      data-testid="datastructureVersionInfo"
    >
      <span className="truncate" title={version.dataStructure.name}>
        {version.dataStructure.name}
      </span>
      <span className="shrink-0">{version.version ? t('version', { version: version.version }) : t('noVersion')}</span>
      <StatusBadge status={version.dataStructureVersionStatus} className="shrink-0" />
    </div>
  )
}
