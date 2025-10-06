import { FormControl, FormDescription, FormField, FormItem, FormLabel } from '@/components/ui/form'
import { Switch as ShadcnSwitch } from '@/components/ui/switch'
import { FieldValue, FieldValues, Path, UseFormReturn } from 'react-hook-form'

interface SwitchProps<T extends FieldValues> {
  form: UseFormReturn<T>
  name: Path<T>
  label: string
}
export const Switch = <T extends FieldValues>(props: SwitchProps<T>) => {
  const { form, label, name } = props
  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <ShadcnSwitch checked={field.value} onCheckedChange={field.onChange} className='hover:cursor-pointer'/>
          </FormControl>
        </FormItem>
      )}
    />
  )
}
