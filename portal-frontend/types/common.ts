import { InputHTMLAttributes } from 'react'

export type Item = {
  id: string
  title: string
}

export type Item2 = {
  id: string
  name: string
}

export type SelectOption = {
  value: string
  label: string
}

export type InputPropsWithoutForm = Omit<InputHTMLAttributes<HTMLInputElement>, 'form' | 'onChange'>
