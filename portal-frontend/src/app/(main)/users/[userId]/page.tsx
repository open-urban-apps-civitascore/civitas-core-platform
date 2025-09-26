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
    const userData = await userResponse.json()
    if (!userData || Object.keys(userData).length === 0) {
      user = null
    } else {
      user = userData
    }
  } catch (error) {
    console.error(error)
  }

  return <div>{user ? JSON.stringify(user) : 'No user found'}</div>
}

export default page
