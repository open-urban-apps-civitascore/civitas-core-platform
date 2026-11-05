import { FieldError, FieldValues, Path, UseFormReturn } from 'react-hook-form'

export const SERVER_ERROR_TYPE = 'server'

// Recursively checks for a true value, because RHF can produce empty nested objects for unchanged fields.
export const hasDirtyField = (val: unknown): boolean => {
  if (val === true) return true
  if (val && typeof val === 'object') return Object.values(val).some(hasDirtyField)
  return false
}

export const pickDirtyValues = <T>(values: T, dirtyFields: Record<string, unknown>): Partial<T> => {
  const result: Partial<T> = {}

  for (const key in dirtyFields) {
    const dirtyValue = dirtyFields[key]
    const value = values[key as keyof T]

    if (dirtyValue === true) {
      result[key as keyof T] = value
    } else if (typeof dirtyValue === 'object' && dirtyValue !== null && typeof value === 'object' && value !== null) {
      const nested = pickDirtyValues(value as Record<string, unknown>, dirtyValue as Record<string, unknown>)

      if (Object.keys(nested).length > 0) {
        result[key as keyof T] = nested as T[keyof T]
      }
    }
  }

  return result
}

const isFieldError = (value: object): value is FieldError => 'type' in value && typeof value.type === 'string'

export const findErrorPaths = (errors: unknown, type: string, path = ''): string[] => {
  if (!errors || typeof errors !== 'object') return []
  if (isFieldError(errors)) return errors.type === type ? [path] : []
  return Object.entries(errors).flatMap(([key, value]) =>
    key === 'ref' ? [] : findErrorPaths(value, type, path ? `${path}.${key}` : key),
  )
}

type ServerError = { path: string; message?: string }

const CONFIGURATION_PATH = 'configuration'

// getFieldState reads the current errors; formState.errors can be an older copy.
export const getServerErrors = <T extends FieldValues>(form: UseFormReturn<T>): ServerError[] => {
  const configurationErrors: unknown = form.getFieldState(CONFIGURATION_PATH as Path<T>).error
  return findErrorPaths(configurationErrors, SERVER_ERROR_TYPE, CONFIGURATION_PATH).map(path => ({
    path,
    message: form.getFieldState(path as Path<T>).error?.message,
  }))
}

export const restoreServerErrors = <T extends FieldValues>(form: UseFormReturn<T>, serverErrors: ServerError[]) => {
  for (const { path, message } of serverErrors) {
    const fieldPath = path as Path<T>
    const hasField = form.getValues(fieldPath) !== undefined
    if (hasField && !form.getFieldState(fieldPath).error) {
      form.setError(fieldPath, { type: SERVER_ERROR_TYPE, message })
    }
  }
}

export const clearServerErrors = <T extends FieldValues>(form: UseFormReturn<T>) => {
  for (const { path } of getServerErrors(form)) {
    form.clearErrors(path as Path<T>)
  }
}

export const triggerKeepingServerErrors = async <T extends FieldValues>(
  form: UseFormReturn<T>,
  paths?: Path<T> | Path<T>[],
  editedPaths: readonly string[] = [],
) => {
  const serverErrors = getServerErrors(form)
  await form.trigger(paths)
  restoreServerErrors(
    form,
    serverErrors.filter(({ path }) => !editedPaths.includes(path)),
  )
}
