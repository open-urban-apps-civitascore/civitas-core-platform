import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { UseFormReturn } from 'react-hook-form'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { TextArea } from '@/components/form/fields/TextArea'
import { TextField } from '@/components/form/fields/TextField'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Form } from '@/components/ui/form'
import { useIsMobile } from '@/hooks/use-mobile'
import { FormRole, ROLE_TYPES } from '@/types/roles'

interface BaseInfoTabProps {
  form: UseFormReturn<FormRole>
  onSubmit: (values: FormRole) => void
  isLoading?: boolean
  roleType?: string
}

export const BaseInfoTab = (props: BaseInfoTabProps) => {
  const { form, onSubmit, isLoading, roleType } = props
  const router = useRouter()
  const tRolesBaseInfo = useTranslations('roles.baseInfoTab')
  const isMobile = useIsMobile()

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col justify-between h-full">
        <ContentCard>
          <DetailsFieldContainer className="pt-0 pb-3 text-xl">
            <SubHeader title={tRolesBaseInfo('heading')} />
          </DetailsFieldContainer>

          <DetailsFieldContainer>
            <TextField
              form={form}
              label={tRolesBaseInfo('form.inputs.name')}
              name="name"
              placeholder={tRolesBaseInfo('form.placeholders.name')}
              formItemProps={{
                className: isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]',
              }}
              required
            />
          </DetailsFieldContainer>

          <DetailsFieldContainer>
            <TextArea
              className="max-w-lg my-12"
              form={form}
              label={tRolesBaseInfo('form.inputs.description')}
              name="description"
              placeholder={tRolesBaseInfo('form.placeholders.description')}
              formItemProps={{
                className: isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]',
              }}
            />
          </DetailsFieldContainer>
        </ContentCard>

        <ActionButtons
          onCancelClick={() => router.push(`/roles?_tab=${roleType || ROLE_TYPES.SYSTEM}`)}
          confirmButtonType="submit"
          isConfirmButtonDisabled={!form.formState.isDirty || isLoading}
          isCancelButtonDisabled={isLoading}
        />
      </form>
    </Form>
  )
}
