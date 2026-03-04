import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { FormCheckbox } from '@/components/form/fields/FormCheckbox'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { InputPropsWithoutForm, SelectOption } from '@/types/common'

export interface DynamicFormFieldProps<T extends FieldValues> extends InputPropsWithoutForm {
  id: string
  type: 'textArea' | 'input' | 'select' | 'checkbox'
  form: UseFormReturn<T>
  label: string
  name: Path<T>
  placeholder: string
  options?: SelectOption[]
  shouldShowErrors: boolean
  className?: string
}
export const DynamicFormField = <T extends FieldValues>(props: DynamicFormFieldProps<T>) => {
  const {
    id,
    type,
    form,
    label,
    name,
    placeholder,
    required = false,
    options = [],
    shouldShowErrors,
    className,
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
        />
      )
    case 'checkbox':
      return <FormCheckbox form={form} name={name} label={label} required={required} formItemProps={{ className }} />
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
        />
      )
  }
}
