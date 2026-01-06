import axios from 'axios'

export const axiosClient = axios.create({
  baseURL: process.env.NEXT_SERVER_URL,
  withCredentials: true,
})
