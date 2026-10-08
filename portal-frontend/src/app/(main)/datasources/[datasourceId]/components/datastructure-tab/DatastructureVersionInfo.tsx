import { CircleCheckBig, CircleDashed } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { Badge } from '@/components/ui/badge'
import { DATASTRUCTURE_STATUS_TYPES, DatastructureVersion } from '@/types/datastructures'

interface DatastructureVersionInfoProps {
  version: DatastructureVersion
}

export const DatastructureVersionInfo = (props: DatastructureVersionInfoProps) => {
  const { version } = props
  const t = useTranslations('datasources.dataModel.versionInfo')
  const tCommon = useTranslations('common')
  const status = version.dataStructureVersionStatus

  return (
    <div
      className="flex items-center gap-6 text-sm font-semibold text-foreground"
      data-testid="datastructureVersionInfo"
    >
      <span>{version.dataStructure.name}</span>
      <span>{version.version ? t('version', { version: version.version }) : t('noVersion')}</span>
      <Badge variant="secondary" className="text-sm">
        {status === DATASTRUCTURE_STATUS_TYPES.DRAFT ? <CircleDashed /> : <CircleCheckBig />}
        {tCommon(`status.${status}`)}
      </Badge>
    </div>
  )
}
