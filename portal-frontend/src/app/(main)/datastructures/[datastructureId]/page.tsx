import { getTranslations } from 'next-intl/server'

import { getDatastructure, getDatastructureAssignments } from '@/app/services/api/datastructures/serverRequests'
import { NoDataPage } from '@/components/no-data/no-data-page/NoDataPage'
import { DatastructureApiResponseSchema } from '@/types/datastructures'
import { mapAssignmentApiResponseToTable } from '@/utils/assignments'

import { DatastructureOverview } from './components/DatastructureOverview'

type EditDatastructurePageProps = {
  params: Promise<{ datastructureId: string }>
}

const EditDatastructurePage = async ({ params }: EditDatastructurePageProps) => {
  const { datastructureId } = await params
  const t = await getTranslations('common')

  const [datastructureResponse, assignmentsResponse] = await Promise.all([
    getDatastructure(datastructureId),
    getDatastructureAssignments(datastructureId),
  ])

  const parsedDatastructure = DatastructureApiResponseSchema.safeParse(datastructureResponse.data)
  if (!parsedDatastructure.success) {
    console.error(t('errors.loadingError'), parsedDatastructure)
    return <NoDataPage title={t('errors.loadingError')} />
  }

  const initialAssignments = mapAssignmentApiResponseToTable(assignmentsResponse.data)

  return <DatastructureOverview datastructure={parsedDatastructure.data} initialAssignments={initialAssignments} />
}

export default EditDatastructurePage
