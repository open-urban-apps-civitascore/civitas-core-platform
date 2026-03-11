import Axios, { AxiosRequestConfig } from 'axios'

export const AXIOS_INSTANCE = Axios.create({ baseURL: '' })

export const customInstance = <T>(config: AxiosRequestConfig): Promise<T> => {
  const controller = new AbortController()
  const promise = AXIOS_INSTANCE({ ...config, signal: controller.signal }).then(({ data }) => data)

  // @ts-ignore
  promise.cancel = () => {
    controller.abort()
  }

  return promise
}
