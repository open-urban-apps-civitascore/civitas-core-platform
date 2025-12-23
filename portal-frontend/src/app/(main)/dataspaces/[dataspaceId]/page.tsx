'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useParams, useRouter } from 'next/navigation'
import { useCallback, useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'

import { DataSpace, DataSpaceFormData, dataSpaceSchema } from '../../../../../types/dataspaces'
import { DataSpaceForm } from '../components/DataSpaceForm'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

const EditDataSpacePage = () => {
  const router = useRouter()
  const params = useParams<{ dataspaceId: string }>()
  const { dataspaceId } = params

  const [selectedDataSpace, setSelectedDataSpace] = useState<DataSpace | undefined>(undefined)

  const form = useForm<DataSpaceFormData>({
    resolver: zodResolver(dataSpaceSchema),
    defaultValues: {
      name: '',
      description: '',
      protected: false,
    },
  })

  const getDataSpace = useCallback(async (dataspaceId: DataSpace['id']) => {
    try {
      const response = await fetch(`${URL}/dataspaces/${dataspaceId}`, {
        method: 'GET',
        headers: {
          // eslint-disable-next-line @typescript-eslint/naming-convention
          'Content-Type': 'application/json',
        },
      })

      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`)
      }

      const data = await response.json()
      setSelectedDataSpace(data)
    } catch (error) {
      console.error('Error fetching dataspace:', error)
    }
  }, [])

  useEffect(() => {
    if (dataspaceId) {
      getDataSpace(dataspaceId)
    }
  }, [dataspaceId, getDataSpace])

  useEffect(() => {
    if (selectedDataSpace) {
      form.reset({
        name: selectedDataSpace.name,
        description: selectedDataSpace.description || '',
        protected: selectedDataSpace.protected || false,
      })
    }
  }, [selectedDataSpace, form])

  const updateDataSpace = async (dataspaceId: DataSpace['id'], updatedDataSpaceData: DataSpace) => {
    try {
      const response = await fetch(`${URL}/dataspaces/${dataspaceId}`, {
        method: 'PUT',
        headers: {
          // eslint-disable-next-line @typescript-eslint/naming-convention
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(updatedDataSpaceData),
      })

      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`)
      }
    } catch (error) {
      console.error('Error updating dataspace:', error)
    }
  }

  const deleteDataSpace = async (dataspaceId: DataSpace['id']) => {
    try {
      const response = await fetch(`${URL}/dataspaces/${dataspaceId}`, {
        method: 'DELETE',
        headers: {
          // eslint-disable-next-line @typescript-eslint/naming-convention
          'Content-Type': 'application/json',
        },
      })

      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`)
      }

      router.push('/dataspaces')
    } catch (error) {
      console.error('Error deleting dataspace:', error)
    }
  }

  const onSubmit = async (values: DataSpaceFormData) => {
    if (!selectedDataSpace) return

    await updateDataSpace(dataspaceId, { ...selectedDataSpace, ...values })
    form.reset(values)
    router.refresh()
  }

  return (
    <PageContainer testId="dataspaceDetailsPage" headerType="onlyTitle">
      <PageHeader title={selectedDataSpace?.name} />
      <PageBackground>
        <DataSpaceForm
          form={form}
          onSubmit={onSubmit}
          isEdit={true}
          deleteDataSpace={() => deleteDataSpace(dataspaceId)}
        />
      </PageBackground>
    </PageContainer>
  )
}

export default EditDataSpacePage
