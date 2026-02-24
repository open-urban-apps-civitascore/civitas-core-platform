import { AxiosRequestConfig } from 'axios'
import { InputHTMLAttributes } from 'react'
import z from 'zod'

export type Item = {
  id: string
  title: string
}

export const ItemSchema = z.object({
  id: z.string(),
  name: z.string(),
})

export type ItemType = z.infer<typeof ItemSchema>

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
  endpoint?: string
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
  endpoint?: string
  errorMessage: string
  headers?: AxiosRequestConfig['headers']
}

export type CreateMutationInput = BaseMutationInput

export type UpdateMutationInput<TData> = Omit<BaseMutationInput, 'endpoint'> & {
  method: UpdateMutationMethod
  endpoint?: (data: TData) => string
}

export type DeleteMutationInput = BaseMutationInput & {
  id: string
}
