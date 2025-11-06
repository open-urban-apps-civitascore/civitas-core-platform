'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'

import { DataSpaceFormData } from '../../../../../types/dataspaces'
import { DataSpaceForm } from '../components/DataSpaceForm'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

const dataSpaceSchema = z.object({
  name: z.string().min(2, 'Name must be at least 2 characters.').max(50, 'Name must be at most 50 characters.'),
  description: z
    .string()
    .min(10, 'Description must be at least 10 characters.')
    .max(500, 'Description must be at most 500 characters.'),
  protected: z.boolean(),
})

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

  const postDataSpace = async (dataSpaceInput: DataSpaceFormData): Promise<void> => {
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

      form.reset()
      router.push('/dataspaces')
    } catch (error) {
      console.error('Error:', error)
    }
  }

  const onSubmit = (values: DataSpaceFormData) => {
    postDataSpace(values)
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
