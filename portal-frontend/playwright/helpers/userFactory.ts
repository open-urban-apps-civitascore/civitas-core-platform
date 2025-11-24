import { UserResponse } from '@/types/users'

import { E2E_MOCK_FIRSTNAME, E2E_MOCK_LASTNAME } from '../../playwright.config'

export const getMockUserData = (overrides: Partial<UserResponse> = {}): UserResponse => {
  const id = crypto.randomUUID()
  return {
    id: id,
    title: 'female',
    firstName: `${E2E_MOCK_FIRSTNAME}-${id}`,
    lastName: `${E2E_MOCK_LASTNAME}-${id}`,
    email: `${E2E_MOCK_FIRSTNAME}-${id}@${E2E_MOCK_LASTNAME}.test`,
    authority: null,
    groups: [],
    phone: '+49 157 11111111',
    active: true,
    positionDescription: 'Test Description',
    displayName: `${E2E_MOCK_FIRSTNAME}-${id} ${E2E_MOCK_LASTNAME}-${id}`,
    ...overrides,
  }
}
