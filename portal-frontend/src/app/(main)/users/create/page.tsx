import { UserDetails } from '../components/UserDetails'
import { defaultFormUser } from '../formDefaults'

const page = () => {
  return <UserDetails userData={defaultFormUser} />
}

export default page
