import type { PipelinePayload } from '@/app/(main)/datasets/[datasetId]/data/pipeline-editor/_types/pipeline'
import { useCreateMutation } from '@/hooks/use-create-mutation'

const key = 'pipeline'

const API_REQUEST_HEADER = 'x-api-request'

export const useCreatePipeline = () => {
  return useCreateMutation<unknown, PipelinePayload>({
    key,
    errorMessage: 'An error occurred while saving the pipeline.',
    headers: { [API_REQUEST_HEADER]: 'true' },
  })
}
