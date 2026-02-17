import { DatasourceFormToApiData } from '@/types/datasources'

export const pickDirtyValues = <T extends DatasourceFormToApiData>(
  values: T,
  dirty: Record<string, unknown>,
): Partial<DatasourceFormToApiData> => {
  const result: Partial<T> = {}

  for (const key in dirty) {
    const dirtyValue = dirty[key]
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
