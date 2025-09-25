import { FormUser } from '../page'

interface PageProps {
  params: { slug: string }
}

const page = async (props: PageProps) => {
  const { params } = props
  const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

  let user: FormUser | null = null

  try {
    const userResponse = await fetch(`${URL}/${params.slug}`, {
      cache: 'no-store',
    })
    if (!userResponse) {
      console.error('No user found')
    }
    user = await userResponse.json()
  } catch (error) {
    console.error(error)
  }

  return <div>User Form</div>
}

export default page
