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
