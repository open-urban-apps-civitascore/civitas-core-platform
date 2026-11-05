import { useTranslations } from 'next-intl'
import { ChangeEvent, DetailedHTMLProps, HTMLAttributes, useRef, useState } from 'react'
import { FieldValues, Path, PathValue, UseFormReturn, useWatch } from 'react-hook-form'

import { Button } from '@/components/ui/button'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'

import { DISABLED_INPUT_CLASS_NAME } from './disabledFieldClassNames'

const MASKED_VALUE = '********'

type SelectedFile = { name: string; content: string }

interface FormFileFieldProps<T extends FieldValues> {
  form: UseFormReturn<T>
  name: Path<T>
  label: string
  accept?: string
  // eslint-disable-next-line react/boolean-prop-naming
  required?: boolean
  // eslint-disable-next-line react/boolean-prop-naming
  disabled?: boolean
  formItemProps?: DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement>
}

export const FormFileField = <T extends FieldValues>(props: FormFileFieldProps<T>) => {
  const { form, name, label, accept, required = false, disabled = false, formItemProps } = props
  const t = useTranslations('common.fileField')
  const isMobile = useIsMobile()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [selectedFile, setSelectedFile] = useState<SelectedFile | null>(null)
  const value = useWatch({ control: form.control, name })

  const getFileDisplayText = () => {
    if (selectedFile && selectedFile.content === value) return selectedFile.name
    if (value) return MASKED_VALUE
    return t('noFile')
  }

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    let content: string
    try {
      content = await file.text()
    } catch {
      form.setError(name, { type: 'custom', message: 'common.fileField.unreadable' })
      return
    }
    setSelectedFile({ name: file.name, content })
    form.setValue(name, content as PathValue<T, Path<T>>, { shouldDirty: true, shouldValidate: true })
  }

  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem
          className={cn(
            isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]',
            formItemProps?.className,
          )}
        >
          <FormLabel>
            {label}
            {required && <span className="text-red-500 ml-1">*</span>}
          </FormLabel>
          <div>
            <div className="flex items-center gap-2">
              <FormControl>
                <Input
                  ref={field.ref}
                  data-testid={`${name}FileStatus`}
                  data-test-element="formField"
                  className={DISABLED_INPUT_CLASS_NAME}
                  value={getFileDisplayText()}
                  readOnly
                  disabled={disabled}
                />
              </FormControl>
              <Button type="button" variant="outline" disabled={disabled} onClick={() => fileInputRef.current?.click()}>
                {t('select')}
              </Button>
              <input
                ref={fileInputRef}
                data-testid={`${name}FileInput`}
                type="file"
                accept={accept}
                className="hidden"
                disabled={disabled}
                onChange={handleFileChange}
              />
            </div>
            <FormMessage className="mt-2" />
          </div>
        </FormItem>
      )}
    />
  )
}
