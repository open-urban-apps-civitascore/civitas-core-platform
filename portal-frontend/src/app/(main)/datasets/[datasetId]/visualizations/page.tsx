import { redirect } from 'next/navigation'

import { getDataset } from '@/app/services/api/datasets/serverRequests'
import { isVisualizationTypeQuery } from '@/types/visualizations'

import { VisualizationConfigPage } from './components/VisualizationConfigPage'

interface VisualizationsPageProps {
  params: Promise<{ datasetId: string }>
  searchParams: Promise<{ type?: string }>
}

const VisualizationsPage = async (props: VisualizationsPageProps) => {
  const { datasetId } = await props.params
  const { type } = await props.searchParams

  if (!isVisualizationTypeQuery(type)) {
    redirect(`/datasets/${datasetId}`)
  }

  const { data: dataset } = await getDataset(datasetId)

  return <VisualizationConfigPage testId="visualizationConfigPage" dataset={dataset} visualizationType={type} />
}

export default VisualizationsPage
