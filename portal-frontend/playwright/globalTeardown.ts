import { removeTestUsers } from './removeTestUsers'

const globalTeardown = async () => {
  await removeTestUsers()
}

export default globalTeardown
