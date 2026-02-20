import { getTranslations } from 'next-intl/server'

import { STATUS_TYPES } from '@/types/common'
import { DatastructureVersionSummary, SOURCE } from '@/types/datastructures'

import { VersionOverview } from '../VersionOverview'

export const defaultVersion: DatastructureVersionSummary = {
  id: '',
  versionNumber: '',
  description: '',
  source: SOURCE.OWN,
  status: STATUS_TYPES.DRAFT,
}

interface CreateDatastructureVersionPage {
  params: Promise<{ versionId: string; datastructureId: string }>
}

const CreateDatastructureVersionPage = async ({ params }: CreateDatastructureVersionPage) => {
  const { datastructureId } = await params

  const t = await getTranslations('datastructureVersion')

  return (
    <VersionOverview
      testId="createDatastructureVersionOverview"
      version={defaultVersion}
      datastructureId={datastructureId}
      title={t('newVersion')}
      isCreateMode
    />
  )
}

export default CreateDatastructureVersionPage
