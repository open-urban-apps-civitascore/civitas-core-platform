import type { UMLModelPayload } from '@/app/(main)/uml-modeler/services/modelUploadService'
import { useCreateMutation } from '@/hooks/use-create-mutation'

const key = 'models'

export const useCreateModel = () => {
  return useCreateMutation<unknown, UMLModelPayload>({
    key,
    errorMessage: 'An error occurred while saving the model.',
  })
}
