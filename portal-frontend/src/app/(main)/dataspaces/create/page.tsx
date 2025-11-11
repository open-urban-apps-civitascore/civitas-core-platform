'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useForm } from 'react-hook-form'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'

import { DataSpace, DataSpaceFormData, dataSpaceSchema } from '../../../../../types/dataspaces'
import { DataSpaceForm } from '../components/DataSpaceForm'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

const CreateDataSpacePage = () => {
  const t = useTranslations('dataspaces')
  const router = useRouter()

  const form = useForm<DataSpaceFormData>({
    resolver: zodResolver(dataSpaceSchema),
    defaultValues: {
      name: '',
      description: '',
      protected: false,
    },
  })

  const postDataSpace = async (dataSpaceInput: DataSpaceFormData): Promise<DataSpace | undefined> => {
    try {
      const response = await fetch(`${URL}/dataspaces`, {
        method: 'POST',
        headers: {
          // eslint-disable-next-line @typescript-eslint/naming-convention
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          id: Date.now().toString(), // Simple ID generation for demo
          ...dataSpaceInput,
        }),
      })

      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`)
      }
      const data = await response.json()
      console.log('successfully created data space:', data)
      return data
    } catch (error) {
      console.error('Error:', error)
    }
  }

  const onSubmit = async (values: DataSpaceFormData) => {
    const data = await postDataSpace(values)
    if (data) {
      router.push(`/dataspaces/${data.id}`)
    }
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
