import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { FormCheckbox } from '@/components/form/fields/FormCheckbox'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { FormTextArea } from '@/components/form/fields/FormTextArea'
import { TextField } from '@/components/form/fields/TextField'
import { SelectOption } from '@/types/common'

export interface TypeComponentMappingProps<T extends FieldValues> {
  id?: string
  type: 'textArea' | 'input' | 'select' | 'checkbox'
  form: UseFormReturn<T>
  label: string
  name: Path<T>
  placeholder: string
  options?: SelectOption[]
}
export const FormPropertyField = <T extends FieldValues>(props: TypeComponentMappingProps<T>) => {
  const { id = '', type, form, label, name, placeholder, options = [] } = props
  switch (type) {
    case 'textArea':
      return <FormTextArea form={form} name={name} placeholder={placeholder} label={label} />
    case 'checkbox':
      return <FormCheckbox form={form} name={name} label={label} />
    case 'select':
      return <FormSelect id={id} form={form} name={name} placeholder={placeholder} label={label} options={options} />
    case 'input':
    default:
      return <TextField form={form} name={name} placeholder={placeholder} label={label} />
  }
}
