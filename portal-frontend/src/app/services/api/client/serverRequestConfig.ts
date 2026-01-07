import { headers } from 'next/headers'

export const getServerRequestConfig = async () => {
  return {
    headers: {
      cookie: (await headers()).get('cookie') || '',
      // eslint-disable-next-line @typescript-eslint/naming-convention
      'Cache-Control': 'no-store',
    },
  }
}
