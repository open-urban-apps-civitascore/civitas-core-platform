import { zodResolver } from '@hookform/resolvers/zod'
import { AxiosError } from 'axios'
import { useTranslations } from 'next-intl'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { useCreateDatastructure } from '@/app/services/api/datastructures/clientRequests'
import { useError } from '@/hooks/use-error'
import {
  DatastructureCreateDataSchema,
  DatastructureCreateFormData,
  DatastructureCreateFormSchema,
} from '@/types/datastructures'
import { isNameConflictError } from '@/utils/errors'

export const useDatastructureCreation = () => {
  const t = useTranslations('datastructures')
  const tCommon = useTranslations('common')
  const { handleFormValidationError, handleNameError } = useError()

  const createDatastructure = useCreateDatastructure()
  const isLoading = createDatastructure.isPending

  const form = useForm<DatastructureCreateFormData>({
    resolver: zodResolver(DatastructureCreateFormSchema),
    defaultValues: { name: '' },
  })

  const saveDatastructure = async () => {
    const formData = form.getValues()
    const createDatastructureData = {
      name: formData.name,
      description: '',
      createdFromDataSource: false,
      dataStructureVersionIds: [],
      assignments: [],
    }
    const parsed = DatastructureCreateDataSchema.safeParse(createDatastructureData)
    if (!parsed.success) {
      handleFormValidationError(parsed.error)
      return
    }

    try {
      const response = await createDatastructure.mutateAsync(parsed.data)
      toast.success(tCommon('messages.createSuccess', { item: tCommon('items.datastructure') }))
      return response.data
    } catch (error) {
      // Name-Konflikt gezielt behandeln
      if (isNameConflictError(error as AxiosError)) {
        handleNameError(form, form.getValues('name'))
      } else {
        toast.error(t('errors.creationError'))
      }
      throw error
    }
  }

  return {
    saveDatastructure,
    form,
    isLoading,
  }
}
