'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { X } from 'lucide-react'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { KeyboardEvent, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { Select } from '@/components/form/fields/Select'
import { TextField } from '@/components/form/fields/TextField'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Form, FormLabel } from '@/components/ui/form'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { SelectOption } from '@/types/common'
import { DatasetFormData, DatasetFormSchema } from '@/types/datasets'

import { createDataset, updateDataset } from '../actions'
import { DatasetCompletionStatus } from './CompletionStatus'

interface DatasetOverviewProps {
  dataset: DatasetFormData
  dataspaces: SelectOption[]
  isEditMode: boolean
}
export const DatasetOverview = (props: DatasetOverviewProps) => {
  const { dataset, dataspaces, isEditMode } = props
  const t = useTranslations('datasets')
  const tCommon = useTranslations('common')
  const isMobile = useIsMobile()
  const router = useRouter()
  const [isLoading, setIsLoading] = useState(false)
  const searchParams = useSearchParams()

  const form = useForm<DatasetFormData>({
    resolver: zodResolver(DatasetFormSchema),
    defaultValues: dataset,
  })

  const tagsWatch = form.watch('tags')

  const haveTagsChanged = useMemo(
    () =>
      JSON.stringify(tagsWatch.sort((a, b) => a.localeCompare(b))) !==
      JSON.stringify(dataset.tags.sort((a, b) => a.localeCompare(b))),
    [tagsWatch, dataset.tags],
  )

  const handleAddTag = (event: KeyboardEvent<HTMLInputElement>) => {
    if (
      event.key === 'Enter' &&
      !form.getValues('tags').find(tag => tag.toLocaleLowerCase() === event.currentTarget.value.toLowerCase())
    ) {
      form.setValue('tags', [...new Set([...form.getValues('tags'), event.currentTarget.value])])
      event.currentTarget.value = ''
    }
  }

  const handleRemoveTag = (tag: string) => {
    const filteredTags = tagsWatch.filter(formTag => formTag !== tag)
    form.setValue('tags', filteredTags)
  }

  const handleCreateDataset = async (formData: DatasetFormData) => {
    setIsLoading(true)
    try {
      const response = await createDataset({
        ...formData,
        creator: [],
        department: null,
        distribution: null,
        issued: new Date().toISOString(),
        lastUpdated: new Date().toISOString(),
        dataspace: null,
        status: null,
      })

      router.push(`/datasets/${response.id}`)
    } catch {
      console.error('An error occurred while updating dataset')
    } finally {
      setIsLoading(false)
    }
  }
  const handleUpdateDataset = async (formData: DatasetFormData) => {
    setIsLoading(true)
    const selectedDataspace = dataspaces.find(dataspace => dataspace.value === formData.dataspace)
    const updateDatasetData = {
      ...formData,
      dataspace: selectedDataspace ? { id: selectedDataspace?.value, title: selectedDataspace?.label } : null,
      lastUpdated: new Date().toISOString(),
    }
    try {
      await updateDataset(updateDatasetData)
      form.reset(formData)
      router.refresh()
    } catch {
      console.error('An error occurred while updating dataset')
    } finally {
      setIsLoading(false)
    }
  }
  const handleSubmit = isEditMode ? form.handleSubmit(handleUpdateDataset) : form.handleSubmit(handleCreateDataset)

  if (!dataset) {
    return <NoDataPage title={tCommon('noData')} />
  }

  if (isLoading) {
    return <LoadingSpinner title="Loading..." />
  }

  return (
    <PageContainer headerType="onlyTitle" className="overflow-hidden">
      <PageHeader title={isEditMode ? dataset.name : t('overview.title')} />
      <PageBackground className="overflow-y-auto">
        <ContentCard
          className={cn('h-full grid grid-cols-2 gap-x-[5vw] px-10 overflow-auto', isMobile && 'grid-cols-1')}
        >
          <Form {...form}>
            <form onSubmit={handleSubmit} className="h-full flex flex-col justify-between">
              <div>
                <DetailsFieldContainer>
                  <SubHeader title={t('overview.info.title')} />
                </DetailsFieldContainer>
                <DetailsFieldContainer>
                  <Select
                    id="dataspaceSelect"
                    form={form}
                    label={t('overview.info.dataspace')}
                    name="dataspace"
                    placeholder={t('overview.info.dataspace')}
                    options={dataspaces}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer>
                  <TextField
                    id="datasetTitle"
                    form={form}
                    label={t('overview.info.name')}
                    name="name"
                    placeholder={t('overview.info.name')}
                    required
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer>
                  <TextField
                    id="datasetDescription"
                    form={form}
                    label={t('overview.info.description')}
                    name="description"
                    placeholder={t('overview.info.description')}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer
                  className={cn(
                    'flex items-center',
                    isMobile ? 'grid gap-4 border-b-0' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]',
                  )}
                >
                  <FormLabel className="mb-2 block">{t('overview.info.tags')}</FormLabel>

                  <div>
                    <div
                      className={cn(
                        'flex flex-wrap',
                        'file:text-foreground placeholder:text-muted-foreground selection:bg-primary selection:text-primary-foreground dark:bg-input/30 border-input flex w-full min-w-0 rounded-md border bg-transparent px-0.5 py-1 text-base shadow-xs transition-[color,box-shadow] outline-none file:inline-flex file:h-7 file:border-0 file:bg-transparent file:text-sm file:font-medium disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 md:text-sm',
                        'focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px]',
                        'aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive',
                      )}
                    >
                      {tagsWatch.map(tag => (
                        <Badge key={tag} className="m-0.5">
                          {tag}
                          <Button
                            className="h-auto"
                            style={{ padding: 0 }}
                            size="sm"
                            type="button"
                            onClick={() => handleRemoveTag(tag)}
                          >
                            <X />
                          </Button>
                        </Badge>
                      ))}
                      <input
                        id="datasetTags"
                        placeholder={t('overview.info.typeTag')}
                        onKeyUp={handleAddTag}
                        onKeyDown={e => {
                          if (e.key === 'Enter') e.preventDefault()
                        }}
                        className="flex-1 min-w-26 p-0.5 border-none outline-none shadow-none focus:outline-none focus:ring-0"
                      />
                    </div>
                  </div>
                </DetailsFieldContainer>
              </div>
              <ActionButtons
                confirmButtonType="submit"
                isConfirmButtonDisabled={!form.formState.isDirty && !haveTagsChanged}
                onCancelClick={() => router.push(`/datasets?${searchParams.toString()}`)}
                hasCard={false}
              />
            </form>
          </Form>
          {isEditMode && <DatasetCompletionStatus datasetId={dataset.id} disabled={!isEditMode} />}
        </ContentCard>
      </PageBackground>
    </PageContainer>
  )
}
