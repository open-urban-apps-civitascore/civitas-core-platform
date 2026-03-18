import { signOut } from '@/auth'

// signOut() throws a NEXT_REDIRECT internally and never returns
export const GET = async (): Promise<never> => {
  await signOut({ redirectTo: '/login' })
  throw new Error('signOut should have redirected')
}
