import { UUID } from 'crypto'
import { E2E_MOCK_FIRSTNAME, E2E_MOCK_LASTNAME } from '../../playwright.config'
import { UserFormData, UserResponse } from '@/types/users'

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
