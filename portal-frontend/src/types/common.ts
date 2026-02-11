import { AxiosRequestConfig } from 'axios'
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

export type GetListInput = {
  params?: URLSearchParams
  isEnabled?: boolean
}

export type GetItemInput = {
  id: string
  isEnabled?: boolean
}

export type DataQueryInput = {
  key: string
  errorMessage: string
  id?: string
  params?: URLSearchParams
  isEnabled?: boolean
  headers?: AxiosRequestConfig['headers']
}

export type WithId<T = string> = { id: T }

export type MutationData<TData extends WithId<string | number>> = TData

export type UpdateMutationMethod = 'PUT' | 'PATCH'

export type UpdateInput = {
  id: string
  method: UpdateMutationMethod
}

export type BaseMutationInput = {
  key: string
  errorMessage: string
  headers?: AxiosRequestConfig['headers']
}

export type CreateMutationInput = BaseMutationInput

export type UpdateMutationInput = BaseMutationInput & {
  method: UpdateMutationMethod
}

export type DeleteMutationInput = BaseMutationInput & {
  id: string
}
