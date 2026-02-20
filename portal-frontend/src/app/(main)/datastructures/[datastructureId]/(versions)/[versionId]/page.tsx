import { getTranslations } from 'next-intl/server'

import { getDatastructureVersion } from '@/app/services/api/datastructures/versions/serverRequests'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { DatastructureApiResponseSchema } from '@/types/datastructures'

import { defaultVersion } from '../createVersion/page'
import { VersionOverview } from '../VersionOverview'

interface EditDatastructureVersionPage {
  params: Promise<{ versionId: string; datastructureId: string }>
}

const EditDatastructureVersionPage = async ({ params }: EditDatastructureVersionPage) => {
  const { versionId, datastructureId } = await params
  const t = await getTranslations('common')
  const datastructureResponse = await getDatastructureVersion(datastructureId, versionId)
  const parsedDatastructure = DatastructureApiResponseSchema.safeParse(datastructureResponse.data)
  if (!parsedDatastructure.success) {
    console.error(t('errors.loadingError'), parsedDatastructure)
    return <NoDataPage title={t('errors.loadingError')} />
  }

  return (
    <VersionOverview
      title=""
      datastructureId={datastructureId}
      version={parsedDatastructure.data.versions.find(version => version.id === versionId) || defaultVersion}
      isCreateMode={false}
      testId="editDatastructureVersionOverview"
    />
  )
}

export default EditDatastructureVersionPage
