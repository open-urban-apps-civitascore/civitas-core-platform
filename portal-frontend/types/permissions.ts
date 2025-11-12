import { ROLE_TYPES } from './roles'
import { Category } from './users'

export type Permission = {
  id: string
  title: string
  category: Category
  type: (typeof ROLE_TYPES)[keyof typeof ROLE_TYPES]
}
