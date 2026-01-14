'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useForm } from 'react-hook-form'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'

import { DataSpace, DataSpaceFormData, dataSpaceSchema } from '../../../../../types/dataspaces'
import { DataSpaceForm } from '../components/DataSpaceForm'

const CreateDataSpacePage = () => {
  const t = useTranslations('dataspaces')
  const router = useRouter()
  const queryClient = useQueryClient()

  const createDataSpaceMutation = useMutation({
    mutationFn: (dataSpaceInput: DataSpaceFormData) =>
      apiRequest<DataSpace>({
        method: 'POST',
        endpoint: '/dataspaces',
        data: dataSpaceInput,
        errorMessage: 'An error occurred while creating the data space.',
      }),
    onSuccess: ({ data }) => {
      queryClient.invalidateQueries({
        queryKey: ['dataspaces'],
      })
      router.push(`/dataspaces/${data.id}`)
    },
    onError: error => {
      console.error('Failed to create dataspace.', error)
    },
  })

  const form = useForm<DataSpaceFormData>({
    resolver: zodResolver(dataSpaceSchema),
    defaultValues: {
      name: '',
      description: '',
      protected: false,
    },
  })

  const onSubmit = async (values: DataSpaceFormData) => {
    createDataSpaceMutation.mutate(values)
  }

  return (
    <PageContainer headerType="onlyTitle">
      <PageHeader title={t('createDataSpace')} />
      <PageBackground>
        <DataSpaceForm form={form} onSubmit={onSubmit} isEdit={true} />
      </PageBackground>
    </PageContainer>
  )
}

export default CreateDataSpacePage
