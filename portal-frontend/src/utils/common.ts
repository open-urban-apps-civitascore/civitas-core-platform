import z from 'zod'

export const enumFromConst = <T extends Record<string, string>>(obj: T) =>
  z.enum(Object.values(obj) as [T[keyof T], ...T[keyof T][]])

export const setFocus = (id: string) => {
  requestAnimationFrame(() => {
    const element = document.getElementById(id)
    element?.focus()
  })
}
