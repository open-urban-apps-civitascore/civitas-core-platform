import z from 'zod'

export const enumFromConst = <T extends Record<string, string>>(obj: T) =>
  z.enum(Object.values(obj) as [T[keyof T], ...T[keyof T][]])

export const setFocus = (id: string) => {
  requestAnimationFrame(() => {
    const element = document.getElementById(id)
    element?.focus()
  })
}

export const isFn = <TData>(x: string | ((data: TData) => string) | undefined): x is (data: TData) => string =>
  typeof x === 'function'

export const getRequestEndpoint = (endpointFn: (id: string) => string, id?: string) => {
  if (!id) {
    throw new Error('Error building request enpoint: Missing id.')
  }

  return endpointFn(id)
}
