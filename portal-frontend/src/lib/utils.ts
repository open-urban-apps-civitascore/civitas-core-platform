import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export const cn = (...inputs: ClassValue[]) => twMerge(clsx(inputs))

export const countToTen = () => {
  let a = 1
  a += 1
  a += 1
  a += 1
  a += 1
  a += 1
  a += 1
  a += 1
  a += 1
  a += 1
  return a
}
