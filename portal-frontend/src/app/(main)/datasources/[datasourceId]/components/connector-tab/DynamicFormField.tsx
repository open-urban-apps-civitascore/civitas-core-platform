import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { FormCheckbox } from '@/components/form/fields/FormCheckbox'
import { FormFileField } from '@/components/form/fields/FormFileField'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { InputPropsWithoutForm, SelectOption } from '@/types/common'
import { FormFieldType, FormInputType } from '@/types/datasources'

export interface DynamicFormFieldProps<T extends FieldValues> extends InputPropsWithoutForm {
  id: string
  type: FormFieldType
  inputType?: FormInputType
  form: UseFormReturn<T>
  label: string
  name: Path<T>
  placeholder: string
  options?: SelectOption[]
  shouldShowErrors: boolean
  className?: string
  normalize?: (value: string) => string
}
export const DynamicFormField = <T extends FieldValues>(props: DynamicFormFieldProps<T>) => {
  const {
    id,
    type,
    inputType,
    form,
    label,
    name,
    placeholder,
    required = false,
    options = [],
    shouldShowErrors,
    className,
    disabled,
    autoComplete,
    normalize,
    accept,
  } = props
  switch (type) {
    case 'textArea':
      return (
        <FormTextArea
          form={form}
          name={name}
          placeholder={placeholder}
          label={label}
          required={required}
          shouldShowErrors={shouldShowErrors}
          formItemProps={{ className }}
          disabled={disabled}
        />
      )
    case 'checkbox':
      return (
        <FormCheckbox
          form={form}
          name={name}
          label={label}
          required={required}
          formItemProps={{ className }}
          disabled={disabled}
        />
      )
    case 'file':
      return (
        <FormFileField
          form={form}
          name={name}
          label={label}
          accept={accept}
          required={required}
          formItemProps={{ className }}
          disabled={disabled}
        />
      )
    case 'select':
      return (
        <FormSelect
          id={id}
          form={form}
          name={name}
          placeholder={placeholder}
          label={label}
          options={options}
          required={required}
          formItemProps={{ className }}
          disabled={disabled}
        />
      )
    case 'input':
    default:
      return (
        <TextField
          form={form}
          name={name}
          placeholder={placeholder}
          label={label}
          required={required}
          shouldShowErrors={shouldShowErrors}
          formItemProps={{ className }}
          disabled={disabled}
          type={inputType}
          autoComplete={autoComplete}
          normalize={normalize}
        />
      )
  }
}
