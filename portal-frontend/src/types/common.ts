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

export const STATUS_TYPES = {
  DRAFT: 'DRAFT',
  AVAILABLE: 'AVAILABLE',
  READY: 'READY',
} as const

export type StatusTypes = (typeof STATUS_TYPES)[keyof typeof STATUS_TYPES]

export type InputPropsWithoutForm = Omit<InputHTMLAttributes<HTMLInputElement>, 'form' | 'onChange'>

export type GetListInput = {
  params?: URLSearchParams
  isEnabled?: boolean
  queryKey?: string
}

export type GetItemInput = {
  id: string
  isEnabled?: boolean
}

export type DataQueryInput = {
  key: string
  queryKey?: string
  errorMessage: string
  headers?: AxiosRequestConfig['headers']
  id?: string
  params?: URLSearchParams
  isEnabled?: boolean
}

export type WithId<T = string> = { id: T }

export type MutationData<TData extends WithId<string | number>> = TData

export type UpdateMutationMethod = 'PUT' | 'PATCH'

export type UpdateInput = {
  id: string
  method: UpdateMutationMethod
}

export type BaseMutationInput<TData> = {
  key: string
  endpoint?: string | ((value: TData) => string)
  errorMessage: string
  headers?: AxiosRequestConfig['headers']
}

export type CreateMutationInput<TData> = BaseMutationInput<TData>

export type UpdateMutationInput<TData> = BaseMutationInput<TData> & {
  method: UpdateMutationMethod
}

export type DeleteMutationInput<TData> = BaseMutationInput<TData>
