import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { UseFormReturn } from 'react-hook-form'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { Switch as CommonSwitch } from '@/components/form/fields/Switch'
import { TextArea } from '@/components/form/fields/TextArea'
import { TextField } from '@/components/form/fields/TextField'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { DataSpaceFormData } from '@/types/dataspaces'

interface DataSpaceFormProps {
  form: UseFormReturn<DataSpaceFormData>
  onSubmit: (values: DataSpaceFormData) => void
  deleteDataSpace?: () => void
  isEdit: boolean
}

export const DataSpaceForm = (props: DataSpaceFormProps) => {
  const { form, onSubmit, deleteDataSpace, isEdit } = props
  const tDataSpaces = useTranslations('dataspaces')
  const tCommon = useTranslations('common')

  const router = useRouter()

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)}>
        <div className="flex flex-col flex-grow justify-between h-[calc(100vh-200px)] mt-4">
          <ContentCard>
            <div className="w-1/2">
              <TextField
                form={form}
                name="name"
                label={tDataSpaces('form.inputs.name')}
                placeholder=""
                required={true}
                formItemProps={{ className: 'mb-6' }}
              />

              <TextArea
                form={form}
                name="description"
                label={tDataSpaces('form.inputs.description')}
                placeholder=""
                required={true}
                formItemProps={{ className: 'mb-6' }}
              />

              <CommonSwitch form={form} name="protected" label={tDataSpaces('form.inputs.protected')} />
            </div>
          </ContentCard>

          <div className="flex items-end">
            {isEdit && deleteDataSpace && (
              <Button type="button" variant="outline" onClick={() => deleteDataSpace()}>
                {tCommon('actions.delete')}
              </Button>
            )}

            <div className="ml-auto">
              <ActionButtons
                confirmButtonType="submit"
                onCancelClick={() => router.push('/dataspaces')}
                hasCard={false}
                isConfirmButtonDisabled={!form.formState.isDirty}
              />
            </div>
          </div>
        </div>
      </form>
    </Form>
  )
}
