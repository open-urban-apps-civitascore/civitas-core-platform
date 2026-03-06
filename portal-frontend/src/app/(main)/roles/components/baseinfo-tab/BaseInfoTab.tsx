import { useTranslations } from 'next-intl'
import { useEffect, useMemo } from 'react'
import { UseFormReturn } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Button } from '@/components/ui/button'
import { Form } from '@/components/ui/form'
import { useIsMobile } from '@/hooks/use-mobile'
import { FormRole } from '@/types/roles'

interface BaseInfoTabProps {
  form: UseFormReturn<FormRole>
  isDefaultRole: boolean
  isReadOnly: boolean
  deleteRole: () => void
}

export const BaseInfoTab = (props: BaseInfoTabProps) => {
  const { form, isDefaultRole, isReadOnly, deleteRole } = props
  const tRolesBaseInfo = useTranslations('roles.baseInfoTab')
  const tRoles = useTranslations('roles')
  const isMobile = useIsMobile()

  const isReadonly = useMemo(() => isDefaultRole, [isDefaultRole])

  useEffect(() => {
    form.setValue('readonly', isReadonly)
  }, [isReadonly, form])

  return (
    <Form {...form}>
      <form onSubmit={e => e.preventDefault()} className="flex flex-col gap-5 h-full">
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
              required={!isDefaultRole && !isReadOnly}
              disabled={isReadOnly}
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
              disabled={isReadOnly}
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

        {!isDefaultRole && !isReadOnly && (
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
      </form>
    </Form>
  )
}
