'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useForm } from 'react-hook-form'

import { useCreateDataspace } from '@/app/services/api/dataspaces/clientRequests'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { DataSpaceFormData, dataSpaceSchema } from '@/types/dataspaces'

import { DataSpaceForm } from '../components/DataSpaceForm'

const CreateDataSpacePage = () => {
  const t = useTranslations('dataspaces')
  const router = useRouter()
  const createDataspace = useCreateDataspace()

  const form = useForm<DataSpaceFormData>({
    resolver: zodResolver(dataSpaceSchema),
    defaultValues: {
      name: '',
      description: '',
      protected: false,
    },
  })

  const onSubmit = async (values: DataSpaceFormData) => {
    createDataspace.mutate(values, { onSuccess: ({ data }) => router.push(`/dataspaces/${data.id}`) })
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
