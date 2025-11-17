'use client'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { Select, SelectOption } from '@/components/form/fields/Select'
import { TextField } from '@/components/form/fields/TextField'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Form, FormLabel } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { DatasetFormData, DatasetFormSchema, DatasetOverviewData } from '@/types/datasets'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslations } from 'next-intl'
import { useRouter } from 'next/navigation'
import { KeyboardEvent } from 'react'
import { useForm } from 'react-hook-form'

interface DatasetOverviewProps {
  dataset: DatasetOverviewData
  dataspaces: SelectOption[]
  isEditMode: boolean
}
export const DatasetOverview = (props: DatasetOverviewProps) => {
  const { dataset, dataspaces, isEditMode } = props
  const { creationProgress, ...datasetInfo } = dataset
  const t = useTranslations('datasets')
  const isMobile = useIsMobile()
  const router = useRouter()

  const form = useForm<DatasetFormData>({
    resolver: zodResolver(DatasetFormSchema),
    defaultValues: datasetInfo,
  })

  const tagsWatch = form.watch('tags')

  const handleAddTag = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault()
      form.setValue('tags', [...new Set([...form.getValues('tags'), event.currentTarget.value])])
      event.currentTarget.value = ''
    }
  }

  const createDataset = () => {}
  const updateDataset = () => {}
  const handleSubmit = () => (isEditMode ? updateDataset() : createDataset())

  return (
    <PageContainer headerType="withSubTabs" className="overflow-hidden">
      <PageHeader title={isEditMode ? dataset.title : t('create.title')} />
      <PageBackground className="overflow-y-auto">
        <ContentCard className="h-full grid grid-cols-2">
          <Form {...form}>
            <form onSubmit={handleSubmit} className="h-full flex flex-col justify-between">
              <div>
                <DetailsFieldContainer>
                  <Select
                    id="dataspaceSelect"
                    form={form}
                    label={t('create.info.dataspace')}
                    name="dataspace"
                    placeholder={t('create.info.dataspace')}
                    options={dataspaces}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer>
                  <TextField
                    id="datasetTitle"
                    form={form}
                    label={t('create.info.name')}
                    name="title"
                    placeholder={t('create.info.name')}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer>
                  <TextField
                    id="datasetDescription"
                    form={form}
                    label={t('create.info.description')}
                    name="description"
                    placeholder={t('create.info.description')}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer
                  className={cn(isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]')}
                >
                  <FormLabel className="mb-2 block">{t('create.info.tags')}</FormLabel>

                  <Input id="datasetTags" placeholder={t('create.info.tags')} onKeyUp={handleAddTag} />
                  <div>
                    {tagsWatch.map(tag => (
                      <Badge key={tag}>{tag}</Badge>
                    ))}
                  </div>
                </DetailsFieldContainer>
              </div>
              <ActionButtons
                confirmButtonType="submit"
                isConfirmButtonDisabled={!form.formState.isDirty}
                onCancelClick={() => router.push('datasets')}
                hasCard={false}
              />
            </form>
          </Form>
        </ContentCard>
      </PageBackground>
    </PageContainer>
  )
}
