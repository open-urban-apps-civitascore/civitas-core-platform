import { headers } from 'next/headers'

export const getServerRequestHeaders = async () => {
  return {
    cookie: (await headers()).get('cookie') || '',
  }
}
