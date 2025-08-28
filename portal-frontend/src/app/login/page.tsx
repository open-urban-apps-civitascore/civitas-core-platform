import { signIn } from '../../../auth'
import { Button } from '@/components/ui/button'
import Image from 'next/image'

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ callbackUrl?: string }>
}) {
  const params = await searchParams
  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md w-full space-y-8 p-8">
        <div className="text-center">
          <Image 
            src={"/images/logo_civitas.png"}
            alt="Civitas Logo" 
            width={200}
            height={37.5}
            className="mx-auto"
          />

        </div>

        <form
          action={async () => {
            'use server'
            const callbackUrl = params.callbackUrl || '/'
            await signIn('keycloak', { redirectTo: callbackUrl })
          }}
        >
          <Button type="submit" className="w-full">
            Sign in with Keycloak
          </Button>
        </form>
      </div>
    </div>
  )
}
