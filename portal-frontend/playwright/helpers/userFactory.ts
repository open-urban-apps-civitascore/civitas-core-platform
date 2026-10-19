import { User } from '@/types/users'

import { TEST_ENV } from '../../playwright.config'

export const getMockUserData = (overrides: Partial<User> = {}): User => {
  const id = crypto.randomUUID()
  const firstName = `E2EUserFirstName-${TEST_ENV}-${id}`
  const lastName = 'E2EUserLastName'
  return {
    id: id,
    title: 'MS',
    firstName: firstName,
    lastName: lastName,
    email: `${firstName}@e2e.test`,
    groups: [],
    phone: '+49 157 11111111',
    ...overrides,
  }
}
