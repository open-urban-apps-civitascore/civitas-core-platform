export const getClientRequestConfig = async () => {
  return {
    headers: {
      // eslint-disable-next-line @typescript-eslint/naming-convention
      'Cache-Control': 'no-store',
    },
  }
}
