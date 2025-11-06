import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { UseFormReturn } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { Button } from '@/components/ui/button'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
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
              <FormField
                control={form.control}
                name="name"
                render={({ field }) => (
                  <FormItem className="mb-6">
                    <FormLabel className="mb-1.5">{tDataSpaces('form.inputs.name')}</FormLabel>
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
                  <FormItem className="mb-6">
                    <FormLabel className="mb-1.5">{tDataSpaces('form.inputs.description')}</FormLabel>
                    <FormControl>
                      <Textarea {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="protected"
                render={({ field }) => (
                  <FormItem className="flex flex-row items-center justify-between rounded-lg border p-4">
                    <div className="space-y-0.5">
                      <FormLabel className="text-base">{tDataSpaces('form.inputs.protected')}</FormLabel>
                      <div className="text-sm text-muted-foreground">
                        {tDataSpaces('form.inputs.protectedDescription')}
                      </div>
                    </div>
                    <FormControl>
                      <Switch checked={field.value} onCheckedChange={field.onChange} />
                    </FormControl>
                  </FormItem>
                )}
              />
            </div>
          </ContentCard>

          <div className="flex items-end">
            {isEdit && deleteDataSpace && (
              <Button type="button" variant="outline" onClick={() => deleteDataSpace()}>
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
