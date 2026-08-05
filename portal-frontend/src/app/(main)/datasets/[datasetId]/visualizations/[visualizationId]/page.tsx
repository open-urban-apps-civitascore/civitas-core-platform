import { notFound } from 'next/navigation'

import { getDataset } from '@/app/services/api/datasets/serverRequests'
import { getVisualization } from '@/app/services/api/visualizations/serverRequests'
import { toVisualization } from '@/types/visualizations'

import { VisualizationConfigPage } from '../components/VisualizationConfigPage'

interface VisualizationDetailPageProps {
  params: Promise<{ datasetId: string; visualizationId: string }>
}

const VisualizationDetailPage = async (props: VisualizationDetailPageProps) => {
  const { datasetId, visualizationId } = await props.params

  const [{ data: dataset }, visualizationResult] = await Promise.all([
    getDataset(datasetId),
    getVisualization(datasetId, visualizationId).catch(() => null),
  ])

  if (!visualizationResult) {
    notFound()
  }

  return (
    <VisualizationConfigPage
      testId="visualizationDetailPage"
      dataset={dataset}
      existingVisualization={toVisualization(visualizationResult.data)}
    />
  )
}

export default VisualizationDetailPage
