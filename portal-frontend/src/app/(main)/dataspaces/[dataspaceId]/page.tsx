'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useParams, useRouter } from 'next/navigation'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'

import { useDeleteDataSpace, useGetDataspace, useUpdateDataspace } from '@/app/services/api/dataspaces/clientRequests'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { DataSpaceFormData, dataSpaceSchema } from '@/types/dataspaces'

import { DataSpaceForm } from '../components/DataSpaceForm'
import { mapDataspacesToFormData } from '../utils/mappers'

const defaultDataspace = {
  name: '',
  description: '',
  protected: false,
}
const EditDataSpacePage = () => {
  const params = useParams<{ dataspaceId: string }>()
  const { dataspaceId } = params
  const router = useRouter()

  const updateDataspace = useUpdateDataspace()
  const deleteDataspace = useDeleteDataSpace()

  const { data: dataspace } = useGetDataspace({ id: dataspaceId })

  const form = useForm<DataSpaceFormData>({
    resolver: zodResolver(dataSpaceSchema),
    defaultValues: defaultDataspace,
  })

  useEffect(() => {
    if (dataspace?.data) {
      form.reset(mapDataspacesToFormData(dataspace.data))
    }
  }, [dataspace?.data, form])

  const handleDeleteDataspace = () =>
    deleteDataspace.mutate(dataspaceId, { onSuccess: () => router.push('/dataspaces') })

  const onSubmit = async (values: DataSpaceFormData) => {
    if (!dataspace?.data) return
    updateDataspace.mutate(
      { ...dataspace.data, ...values },
      { onSuccess: ({ data }) => form.reset(mapDataspacesToFormData(data)) },
    )
  }

  return (
    <PageContainer testId="dataspaceDetailsPage" headerType="onlyTitle">
      <PageHeader title={dataspace?.data?.name} />
      <PageBackground>
        <DataSpaceForm form={form} onSubmit={onSubmit} isEdit={true} deleteDataSpace={handleDeleteDataspace} />
      </PageBackground>
    </PageContainer>
  )
}

export default EditDataSpacePage
