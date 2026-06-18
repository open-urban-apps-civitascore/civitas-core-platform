import { getTranslations } from 'next-intl/server'

import { getDatapool, getDatapoolAssignments } from '@/app/services/api/datapools/serverRequests'
import { DatapoolApiResponseSchema } from '@/types/datapools'
import { mapAssignmentApiResponseToTable } from '@/utils/assignments'

import { DatapoolOverview } from './components/DatapoolOverview'

type Props = {
  params: Promise<{ datapoolId: string }>
}

const DatapoolDetailsPage = async ({ params }: Props) => {
  const { datapoolId } = await params
  const t = await getTranslations('common')
  const [datapool, assignmentsResponse] = await Promise.all([
    getDatapool(datapoolId),
    getDatapoolAssignments(datapoolId),
  ])

  const parsedDatapool = DatapoolApiResponseSchema.safeParse(datapool.data)
  if (!parsedDatapool.success) {
    console.error(t('errors.loadingError'), parsedDatapool)
    throw new Error()
  }

  const initialAssignments = mapAssignmentApiResponseToTable(assignmentsResponse.data)

  return <DatapoolOverview datapool={parsedDatapool.data} initialAssignments={initialAssignments} />
}

export default DatapoolDetailsPage
