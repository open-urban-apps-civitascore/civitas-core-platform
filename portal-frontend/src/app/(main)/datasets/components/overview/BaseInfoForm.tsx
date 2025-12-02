import { zodResolver } from '@hookform/resolvers/zod'
import { Circle, CircleCheckBig, SquarePen, X } from 'lucide-react'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { Dispatch, KeyboardEvent, SetStateAction, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { Select } from '@/components/form/fields/Select'
import { TextField } from '@/components/form/fields/TextField'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Form, FormItem, FormLabel } from '@/components/ui/form'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { SelectOption } from '@/types/common'
import { DatasetFormData, DatasetFormSchema } from '@/types/datasets'

import { createDataset, updateDataset } from '../../actions'

interface BaseInfoFormProps {
  dataset: DatasetFormData
  isEditMode: boolean
  dataspaces: SelectOption[]
  setIsLoading: Dispatch<SetStateAction<boolean>>
}
export const BaseInfoForm = (props: BaseInfoFormProps) => {
  const { dataset, dataspaces, isEditMode, setIsLoading } = props
  const t = useTranslations('datasets')
  const tCommon = useTranslations('common')

  const isMobile = useIsMobile()
  const router = useRouter()
  const searchParams = useSearchParams()
  const [isReadOnly, setIsReadOnly] = useState(isEditMode)

  const form = useForm<DatasetFormData>({
    resolver: zodResolver(DatasetFormSchema),
    defaultValues: dataset,
  })

  const tagsWatch = form.watch('tags')

  const haveTagsChanged = useMemo(
    () =>
      JSON.stringify([...tagsWatch].sort((a, b) => a.localeCompare(b))) !==
      JSON.stringify([...dataset.tags].sort((a, b) => a.localeCompare(b))),
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

  const EditButton = (
    <Button variant="outline" type="button" onClick={() => setIsReadOnly(false)}>
      <SquarePen />
      {tCommon('actions.edit')}
    </Button>
  )

  return (
    <Form {...form}>
      <form data-testid="datasetBaseInfoForm" onSubmit={handleSubmit} className={cn('flex gap-2 pt-2')}>
        {dataset.name ? <CircleCheckBig /> : <Circle />}
        <div className={cn('w-full flex flex-col')}>
          <DetailsFieldContainer className="max-w-250 pt-0">
            <SubHeader
              title={t('overview.info.title')}
              titleClassName="text-2xl leading-none font-bold"
              customElement={isEditMode && isReadOnly && EditButton}
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="max-w-250">
            <Select
              id="dataspaceSelect"
              form={form}
              label={t('overview.info.dataspace')}
              name="dataspace"
              placeholder={t('overview.info.dataspace')}
              options={dataspaces}
              disabled={isReadOnly}
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="max-w-250">
            <TextField
              id="datasetTitle"
              form={form}
              label={t('overview.info.name')}
              name="name"
              placeholder={t('overview.info.name')}
              disabled={isReadOnly}
              required
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="max-w-250">
            <TextField
              id="datasetDescription"
              form={form}
              label={t('overview.info.description')}
              name="description"
              placeholder={t('overview.info.description')}
              disabled={isReadOnly}
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer className={cn('mb-6 max-w-250')}>
            <FormItem className={cn(isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]')}>
              <FormLabel>{t('overview.info.tags')}</FormLabel>
              <div>
                <div
                  data-testid="tagsField"
                  className={cn(
                    'flex flex-wrap min-h-9',
                    'file:text-foreground selection:bg-primary selection:text-primary-foreground dark:bg-input/30 border-input flex w-full min-w-0 rounded-md border bg-transparent px-0.5 py-0.5 text-base shadow-xs transition-[color,box-shadow] outline-none file:inline-flex file:h-7 file:border-0 file:bg-transparent file:text-sm file:font-medium disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 md:text-sm',
                    'focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px]',
                    'aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive',
                    isReadOnly && 'min-h-8 opacity-100 text-muted-foreground border-hidden shadow-none py-0 px-2',
                  )}
                >
                  {tagsWatch.map(tag => (
                    <Badge key={tag} className="m-0.5">
                      {tag}
                      {!isReadOnly && (
                        <Button
                          className="h-auto"
                          style={{ padding: 0 }}
                          size="sm"
                          type="button"
                          onClick={() => handleRemoveTag(tag)}
                        >
                          <X />
                        </Button>
                      )}
                    </Badge>
                  ))}
                  {(!isReadOnly || (isReadOnly && tagsWatch.length === 0)) && (
                    <input
                      id="datasetTags"
                      data-testid="tagsInput"
                      placeholder={t('overview.info.typeTag')}
                      onKeyUp={handleAddTag}
                      onKeyDown={e => {
                        if (e.key === 'Enter') e.preventDefault()
                      }}
                      disabled={isReadOnly}
                      className={cn(
                        'flex-1 px-1 min-w-26 border-none outline-none shadow-none focus:outline-none focus:ring-0 placeholder:text-muted-foreground',
                        !isReadOnly && 'px-2',
                      )}
                    />
                  )}
                </div>
              </div>
            </FormItem>
          </DetailsFieldContainer>
          {!isReadOnly && (
            <ActionButtons
              confirmButtonType="submit"
              isConfirmButtonDisabled={!form.formState.isDirty && !haveTagsChanged}
              onCancelClick={() => router.push(`/datasets?${searchParams.toString()}`)}
              hasCard={false}
            />
          )}
        </div>
      </form>
    </Form>
  )
}
