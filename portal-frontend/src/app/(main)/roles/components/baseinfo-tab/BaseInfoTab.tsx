import { SquarePen } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { UseFormReturn } from 'react-hook-form'

import { ActionButtons } from '@/components/action-buttons/ActionButtons'
import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { useIsMobile } from '@/hooks/use-mobile'
import { FormRole, ROLE_TYPES } from '@/types/roles'

interface BaseInfoTabProps {
  form: UseFormReturn<FormRole>
  onSubmit: (values: FormRole) => void
  isLoading: boolean
  isDefaultRole: boolean
  isEditMode: boolean
  deleteRole: () => void
  roleType: string
}

export const BaseInfoTab = (props: BaseInfoTabProps) => {
  const { form, onSubmit, isLoading, roleType, isDefaultRole, isEditMode, deleteRole } = props
  const router = useRouter()
  const tCommon = useTranslations('common')
  const tRolesBaseInfo = useTranslations('roles.baseInfoTab')
  const tRoles = useTranslations('roles')
  const isMobile = useIsMobile()
  const [isReadOnly, setIsReadOnly] = useState(isEditMode)

  const EditButton = (
    <Button variant="outline" type="button" onClick={() => setIsReadOnly(false)}>
      <SquarePen />
      {tCommon('actions.edit')}
    </Button>
  )

  const onSubmitHandler = (values: FormRole) => {
    onSubmit(values)
    setIsReadOnly(true)
  }

  const isReadonly = useMemo(() => isDefaultRole, [isDefaultRole])

  useEffect(() => {
    form.setValue('readonly', isReadonly)
  }, [isReadonly, form])

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmitHandler)} className="flex flex-col gap-5 h-full">
        <ContentCard>
          <DetailsFieldContainer className="pt-0 pb-3 text-xl">
            <SubHeader title={tRolesBaseInfo('heading')} customElement={!isDefaultRole && isEditMode && EditButton} />
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
              required={!isDefaultRole && !isReadOnly}
              disabled={isReadOnly || isDefaultRole}
            />
          </DetailsFieldContainer>

          <DetailsFieldContainer>
            <FormTextArea
              className="max-w-lg my-12"
              form={form}
              label={tRolesBaseInfo('form.inputs.description')}
              name="description"
              placeholder={tRolesBaseInfo('form.placeholders.description')}
              formItemProps={{
                className: isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]',
              }}
              disabled={isReadOnly || isDefaultRole}
            />
          </DetailsFieldContainer>

          <DetailsFieldContainer className="border-0">
            <TextField
              form={form}
              label={tRolesBaseInfo('form.inputs.roleOrigin')}
              name="readonly"
              placeholder={isDefaultRole ? tRoles('defaultRole') : tRoles('customRole')}
              formItemProps={{
                className: isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]',
              }}
              disabled={true}
            />
          </DetailsFieldContainer>
        </ContentCard>

        {!isDefaultRole && isEditMode && (
          <ContentCard className="mt-0">
            <DetailsFieldContainer className="pt-0 pb-3 text-xl border-none">
              <SubHeader title={tRolesBaseInfo('securityArea.heading')} className="border-b pb-2" />
              <div className="flex flex-col gap-2 mt-4">
                <h2 className="text-sm">{tRolesBaseInfo('securityArea.description')}</h2>
                <span className="text-[16px] text-muted-foreground">{tRolesBaseInfo('securityArea.note')}</span>
                <Button
                  variant="outline"
                  type="button"
                  className="self-start mt-2"
                  onClick={() => {
                    deleteRole()
                  }}
                >
                  {tRolesBaseInfo('securityArea.deleteButton')}
                </Button>
              </div>
            </DetailsFieldContainer>
          </ContentCard>
        )}

        {!isDefaultRole && !isReadOnly && (
          <ActionButtons
            onCancelClick={() => router.push(`/roles?_tab=${roleType || ROLE_TYPES.SYSTEM}`)}
            confirmButtonType="submit"
            isConfirmButtonDisabled={!form.formState.isDirty || isLoading || isReadOnly}
            isCancelButtonDisabled={isLoading}
          />
        )}
      </form>
    </Form>
  )
}
