import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslations } from 'next-intl'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'

import { useCreateDatastructure } from '@/app/services/api/datastructures/clientRequests'
import {
  DatastructureCreateDataSchema,
  DatastructureCreateFormData,
  DatastructureCreateFormSchema,
} from '@/types/datastructures'

export const useDatastructureCreation = () => {
  const t = useTranslations('datastructures')
  const tCommon = useTranslations('common')

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
      console.error(parsed.error)
      toast.error(tCommon('errors.formInvalid'))
      return
    }

    try {
      const response = await createDatastructure.mutateAsync(parsed.data)
      toast.success(tCommon('messages.createSuccess', { item: tCommon('items.datastructure') }))
      return response.data
    } catch (error) {
      toast.error(t('errors.creationError'))
      throw error
    }
  }

  return {
    saveDatastructure,
    form,
    isLoading,
  }
}
