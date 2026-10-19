import { useTranslations } from 'next-intl'
import { useEffect } from 'react'
import { toast } from 'sonner'

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import { useGetDataSinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import { useGetPipelines } from '@/app/services/api/pipelines/clientRequests'

const LOAD_FAILED_TOAST_ID = 'pipeline-editor-load-failed'

export const useEditorLoadError = (datasetId: string): boolean => {
  const t = useTranslations('pipelineEditor')
  const pipelinesQuery = useGetPipelines(datasetId)
  const dataSinksQuery = useGetDataSinks(datasetId, { isEnabled: !!datasetId })
  const datasetQuery = useGetDataset({ id: datasetId, isEnabled: !!datasetId })

  const hasLoadError = pipelinesQuery.isLoadingError || dataSinksQuery.isLoadingError || datasetQuery.isLoadingError

  useEffect(() => {
    if (hasLoadError) toast.error(t('header.loadFailed'), { id: LOAD_FAILED_TOAST_ID })
  }, [hasLoadError, t])

  return hasLoadError
}
