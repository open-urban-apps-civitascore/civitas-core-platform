import axios from 'axios'

export const axiosClient = axios.create({
  baseURL: process.env.NEXT_SERVER_URL,
  withCredentials: true,
})

// intercept response to normalize API output
axiosClient.interceptors.response.use(res => {
  if (!res.data.content) {
    return {
      ...res,
      data: {
        content: res.data,
        totalElements: Array.isArray(res.data) ? Number(res.headers['x-total-count']) || 0 : undefined,
      },
    }
  }
  return res
})
