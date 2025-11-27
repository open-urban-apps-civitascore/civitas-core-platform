import { DetailedHTMLProps, HTMLAttributes } from 'react'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'

import { StatusLabel } from '@/components/status-label/StatusLabel'
import { FormControl, FormField, FormItem, FormLabel } from '@/components/ui/form'
import { Switch as ShadcnSwitch } from '@/components/ui/switch'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'

interface SwitchProps<T extends FieldValues> {
  form: UseFormReturn<T>
  name: Path<T>
  label: string
  isReadOnly?: boolean
  formItemProps?: DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement>
}
export const Switch = <T extends FieldValues>(props: SwitchProps<T>) => {
  const { form, label, name, isReadOnly = false, formItemProps } = props
  const isMobile = useIsMobile()
  return (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem
          className={cn(isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_auto]', formItemProps?.className)}
        >
          <FormLabel>{label}</FormLabel>
          <FormControl>
            {isReadOnly ? (
              <StatusLabel className="ml-3" isChecked={field.value} />
            ) : (
              <ShadcnSwitch checked={field.value} onCheckedChange={field.onChange} className="hover:cursor-pointer" />
            )}
          </FormControl>
        </FormItem>
      )}
    />
  )
}
