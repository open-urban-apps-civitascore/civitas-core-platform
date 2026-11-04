import { X } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { FieldArrayWithId, UseFormReturn } from 'react-hook-form'

import { DISABLED_INPUT_CLASS_NAME } from '@/components/form/fields/disabledFieldClassNames'
import { Button } from '@/components/ui/button'
import { FormControl, FormField, FormItem, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { HttpApiCallsForm, HttpParameterListPath } from '@/types/connectors'

const PARAMETER_KEYS = ['name', 'value'] as const

interface ApiCallParameterRowsProps {
  form: UseFormReturn<HttpApiCallsForm>
  name: HttpParameterListPath
  fields: FieldArrayWithId<HttpApiCallsForm, HttpParameterListPath>[]
  typeLabel: string
  isDisabled: boolean
  onRemove: (index: number) => void
  onNameChange: (editedPath: string) => void
}

export const ApiCallParameterRows = (props: ApiCallParameterRowsProps) => {
  const { form, name, fields, typeLabel, isDisabled, onRemove, onNameChange } = props
  const t = useTranslations('datasources.apiCallsTab.parameters')

  return (
    <>
      {fields.map((field, index) => (
        <div key={field.id} className="grid grid-cols-[80px_1fr_1fr_36px] items-start gap-3">
          <span className="text-sm font-semibold leading-9">{typeLabel}</span>
          {PARAMETER_KEYS.map(key => (
            <FormField
              key={key}
              control={form.control}
              name={`${name}.${index}.${key}`}
              render={({ field: inputField }) => (
                <FormItem>
                  <FormControl>
                    <Input
                      {...inputField}
                      onChange={event => {
                        inputField.onChange(event)
                        if (key === 'name') onNameChange(inputField.name)
                      }}
                      aria-label={`${typeLabel} ${t(key)}`}
                      className={DISABLED_INPUT_CLASS_NAME}
                      placeholder={isDisabled ? undefined : t(key)}
                      disabled={isDisabled}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          ))}
          {!isDisabled && (
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label={`${typeLabel} ${t('remove')}`}
              onClick={() => onRemove(index)}
            >
              <X />
            </Button>
          )}
        </div>
      ))}
    </>
  )
}
