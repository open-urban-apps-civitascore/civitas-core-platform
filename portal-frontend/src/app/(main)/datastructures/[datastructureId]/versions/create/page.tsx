import { getTranslations } from 'next-intl/server'

import { User } from '@/types/users'
import {
  DatastructureApiResponseSchema,
  DatastructureVersionFormData,
  DatastructureVersionSummary,
  DatastructureVersionSummaryApiResponseSchema,
  SOURCE,
} from '@/types/datastructures'
import { STATUS_TYPES } from '@/types/common'
import { VersionOverview } from '../VersionOverview'
import { getDatastructure } from '@/app/services/api/datastructures/serverRequests'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { getDatastructureVersion } from '@/app/services/api/datastructures/versions/serverRequests'

interface PageProps {
  params: Promise<{ versionId: string; datastructureId: string }>
}

const CreateUserPage = async ({ params }: PageProps) => {
  const { datastructureId } = await params

  const t = await getTranslations('datastructureVersions')

  const versionResponse = await getDatastructureVersion(datastructureId)
  const parsedVersion = DatastructureVersionSummaryApiResponseSchema.safeParse(versionResponse.data)
  if (!parsedVersion.success) {
    console.error(t('errors.loadingError'), parsedVersion)
    return <NoDataPage title={t('errors.loadingError')} />
  }

  return (
    <VersionOverview
      testId="createDatastructureVersionOverview"
      version={parsedVersion.data}
      versionId=""
      title={t('title')}
      isCreateMode
    />
  )
}

export default CreateUserPage
