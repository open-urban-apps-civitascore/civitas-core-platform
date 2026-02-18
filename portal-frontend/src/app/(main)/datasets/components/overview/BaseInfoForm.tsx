import { zodResolver } from '@hookform/resolvers/zod'
import { Circle, CircleCheckBig, SquarePen } from 'lucide-react'
import { useRouter, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

import { useCreateDataset, usePatchDataset } from '@/app/services/api/datasets/clientRequests'
import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { TextField } from '@/components/form/fields/TextField'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { cn } from '@/lib/utils'
import { SelectOption } from '@/types/common'
import { DatasetFormData, DatasetFormSchema } from '@/types/datasets'

import { mapDatasetToFormData } from '../../utils/mappers'

interface BaseInfoFormProps {
  dataset: DatasetFormData
  isEditMode: boolean
  dataspaces: SelectOption[]
}
export const BaseInfoForm = (props: BaseInfoFormProps) => {
  const { dataset, dataspaces, isEditMode } = props
  const t = useTranslations('datasets')
  const tCommon = useTranslations('common')

  const router = useRouter()
  const searchParams = useSearchParams()
  const [isReadOnly, setIsReadOnly] = useState(isEditMode)

  const createDataset = useCreateDataset()
  const updateDataset = usePatchDataset()

  const isLoading = createDataset.isPending || updateDataset.isPending

  const form = useForm<DatasetFormData>({
    resolver: zodResolver(DatasetFormSchema),
    defaultValues: dataset,
  })

  const handleCreateDataset = async (formData: DatasetFormData) => {
    const selectedDataspace = dataspaces.find(dataspace => dataspace.value === formData.dataspace)
    createDataset.mutate(
      {
        ...formData,
        // the contact implementation has to be adjusted once the API is implemented
        contact: null,
        issued: new Date().toISOString(),
        lastUpdated: new Date().toISOString(),
        dataspace: selectedDataspace ? { id: selectedDataspace?.value, name: selectedDataspace?.label } : null,
        access: true,
        status: 'draft',
      },
      {
        onSuccess: ({ data }) => {
          router.push(`/datasets/${data.id}`)
        },
      },
    )
  }
  const handleUpdateDataset = async (formData: DatasetFormData) => {
    const selectedDataspace = dataspaces.find(dataspace => dataspace.value === formData.dataspace)
    updateDataset.mutate(
      {
        ...formData,
        dataspace: selectedDataspace ? { id: selectedDataspace?.value, name: selectedDataspace?.label } : null,
        lastUpdated: new Date().toISOString(),
      },
      {
        onSuccess: ({ data }) => {
          form.reset(mapDatasetToFormData(data))
          router.refresh()
          setIsReadOnly(true)
        },
      },
    )
  }

  const handleSubmit = isEditMode ? form.handleSubmit(handleUpdateDataset) : form.handleSubmit(handleCreateDataset)

  const EditButton = (
    <Button data-testid="editButton" variant="outline" type="button" onClick={() => setIsReadOnly(false)}>
      <SquarePen />
      {tCommon('actions.editBase')}
    </Button>
  )

  return (
    <Form {...form}>
      <form
        data-testid="datasetBaseInfoForm"
        aria-label={`${tCommon('form')} ${t('overview.info.title')}`}
        onSubmit={handleSubmit}
        className={cn('max-w-300 flex gap-2 pt-2')}
      >
        {dataset.name ? <CircleCheckBig /> : <Circle />}
        <div className={cn('w-full flex flex-col')}>
          <DetailsFieldContainer className="pt-0">
            <SubHeader
              title={t('overview.info.title')}
              titleClassName="text-2xl leading-none font-bold"
              customElement={isEditMode && isReadOnly && EditButton}
            />
          </DetailsFieldContainer>
          {isLoading ? (
            <LoadingSpinner className="h-[364px]" />
          ) : (
            <>
              <DetailsFieldContainer className="max-w-300">
                <FormSelect
                  id="dataspaceSelect"
                  form={form}
                  label={t('overview.info.dataspace')}
                  name="dataspace"
                  placeholder={t('overview.info.selectDataspace')}
                  options={dataspaces}
                  disabled={isReadOnly}
                />
              </DetailsFieldContainer>
              <DetailsFieldContainer className="max-w-300">
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
              <DetailsFieldContainer className="max-w-300">
                <TextField
                  id="datasetDescription"
                  form={form}
                  label={t('overview.info.description')}
                  name="description"
                  placeholder={t('overview.info.description')}
                  disabled={isReadOnly}
                />
              </DetailsFieldContainer>
            </>
          )}
          {!isReadOnly && (
            <ActionButtons
              confirmButtonType="submit"
              isConfirmButtonDisabled={!form.formState.isDirty || isLoading}
              onCancelClick={() => router.push(`/datasets?${searchParams.toString()}`)}
              hasCard={false}
            />
          )}
        </div>
      </form>
    </Form>
  )
}
