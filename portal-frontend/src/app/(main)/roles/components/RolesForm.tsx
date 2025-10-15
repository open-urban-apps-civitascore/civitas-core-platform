import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { UseFormReturn } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { FormRole } from '@/types/roles'

interface RolesFormProps {
  form: UseFormReturn<FormRole>
  onSubmit: (values: FormRole) => void
  deleteRole?: () => void
  isEdit: boolean
}

export const RolesForm = (props: RolesFormProps) => {
  const { form, onSubmit, deleteRole, isEdit } = props
  const tRoles = useTranslations('roles')
  const tCommon = useTranslations('common')

  const router = useRouter()

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)}>
        <div className="flex flex-col flex-grow justify-between h-[calc(100vh-200px)] mt-4">
          <ContentCard>
            <div className="w-1/2">
              <FormField
                control={form.control}
                name="name"
                render={({ field }) => (
                  <FormItem className="mb-6">
                    <FormLabel className="mb-1.5">{tRoles('form.inputs.name')}</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="description"
                render={({ field }) => (
                  <FormItem className="mb-4">
                    <FormLabel className="mb-1.5">{tRoles('form.inputs.description')}</FormLabel>
                    <FormControl>
                      <Textarea {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
          </ContentCard>

          <div className="flex items-end">
            {isEdit && (
              <Button type="button" variant="outline" onClick={() => deleteRole && deleteRole()}>
                {tCommon('actions.delete')}
              </Button>
            )}

            <div className="flex gap-2 ml-auto">
              <Button type="reset" variant="secondary" onClick={() => router.back()}>
                {tCommon('actions.cancel')}
              </Button>

              <Button type="submit">{tCommon('actions.submit')}</Button>
            </div>
          </div>
        </div>
      </form>
    </Form>
  )
}
