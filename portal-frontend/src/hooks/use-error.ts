import { useTranslations } from 'next-intl'
import { FieldValues, Path, UseFormReturn } from 'react-hook-form'
import { toast } from 'sonner'

export const useError = () => {
  const tCommon = useTranslations('common')

  const handleFormValidationError = () => {
    toast.error(tCommon('errors.formInvalid'))
  }

  const handleNameError = <TFormData extends FieldValues & { name: string }>(
    form: UseFormReturn<TFormData>,
    name?: string,
  ) => {
    form.setError('name' as Path<TFormData>, { type: 'manual', message: 'common.errors.nameExists' })
    toast.error(tCommon('errors.nameExistsToast', { name: name || '' }))
  }

  const handleUserEmailError = <TFormData extends FieldValues & { email: string }>(
    form: UseFormReturn<TFormData>,
    email?: string,
  ) => {
    form.setError('email' as Path<TFormData>, { type: 'manual', message: 'common.errors.emailExists' })
    toast.error(tCommon('errors.emailExistsToast', { email: email || '' }))
  }

  const handlePermissionsError = () => {
    toast.error(tCommon('errors.insufficientPermissions'))
  }

  return { handleFormValidationError, handleNameError, handleUserEmailError, handlePermissionsError }
}
