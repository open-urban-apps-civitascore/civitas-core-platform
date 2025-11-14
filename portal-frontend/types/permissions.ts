import { Item } from './common'
import { ROLE_TYPES } from './roles'

export type Permission = {
  id: string
  title: string
  category: Item
  type: (typeof ROLE_TYPES)[keyof typeof ROLE_TYPES]
}
