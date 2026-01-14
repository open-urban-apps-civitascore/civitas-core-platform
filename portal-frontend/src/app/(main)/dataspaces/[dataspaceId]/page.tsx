'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams, useRouter } from 'next/navigation'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'

import { DataSpace, DataSpaceFormData, dataSpaceSchema } from '../../../../../types/dataspaces'
import { DataSpaceForm } from '../components/DataSpaceForm'
import { mapDataspacesToFormData } from '../utils/mappers'

const defaultDataspace = {
  name: '',
  description: '',
  protected: false,
}
const EditDataSpacePage = () => {
  const router = useRouter()
  const params = useParams<{ dataspaceId: string }>()
  const queryClient = useQueryClient()

  const { dataspaceId } = params

  const { data: dataspace } = useQuery({
    queryKey: ['dataspace', dataspaceId],
    queryFn: () =>
      apiRequest<DataSpace>({
        method: 'GET',
        endpoint: `/dataspaces/${dataspaceId}`,
        errorMessage: 'An error occurred while fetching dataspace.',
      }),
  })

  const updateDataSpaceMutation = useMutation({
    mutationFn: ({
      dataspaceId,
      updatedDataSpaceData,
    }: {
      dataspaceId: DataSpace['id']
      updatedDataSpaceData: DataSpace
    }) =>
      apiRequest<DataSpace>({
        method: 'PUT',
        endpoint: `/dataspaces/${dataspaceId}`,
        data: updatedDataSpaceData,
        errorMessage: 'An error occurred while updating the data space.',
      }),
    onSuccess: ({ data }) => {
      form.reset(mapDataspacesToFormData(data))
      queryClient.invalidateQueries({
        queryKey: ['dataspace', data.id],
      })
      queryClient.invalidateQueries({
        queryKey: ['dataspaces'],
      })
    },
    onError: error => {
      console.error('Failed to update dataspace.', error)
    },
  })

  const deleteDataSpaceMutation = useMutation({
    mutationFn: (dataspaceId: DataSpace['id']) =>
      apiRequest<DataSpace>({
        method: 'DELETE',
        endpoint: `/dataspaces/${dataspaceId}`,
        errorMessage: 'An error occurred while deleting the data space.',
      }),
    onSuccess: (_, id) => {
      queryClient.setQueriesData<{ data: DataSpace[] }>({ queryKey: ['dataspaces'] }, old =>
        old ? { ...old, data: old.data.filter((d: DataSpace) => d.id !== id) } : old,
      )
      queryClient.invalidateQueries({
        queryKey: ['dataspaces'],
      })
      router.push('/dataspaces')
    },
    onError: error => {
      console.error('Error deleting dataspace.', error)
    },
  })

  const form = useForm<DataSpaceFormData>({
    resolver: zodResolver(dataSpaceSchema),
    defaultValues: defaultDataspace,
  })

  useEffect(() => {
    if (dataspace?.data) {
      form.reset(mapDataspacesToFormData(dataspace.data))
    }
  }, [dataspace?.data, form])

  const onSubmit = async (values: DataSpaceFormData) => {
    if (!dataspace?.data) return
    updateDataSpaceMutation.mutate({ dataspaceId, updatedDataSpaceData: { ...dataspace.data, ...values } })
  }

  return (
    <PageContainer testId="dataspaceDetailsPage" headerType="onlyTitle">
      <PageHeader title={dataspace?.data?.name} />
      <PageBackground>
        <DataSpaceForm
          form={form}
          onSubmit={onSubmit}
          isEdit={true}
          deleteDataSpace={() => deleteDataSpaceMutation.mutate(dataspaceId)}
        />
      </PageBackground>
    </PageContainer>
  )
}

export default EditDataSpacePage
