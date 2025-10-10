export type GroupResponse = {
  id: string
  title: string
  description: string | null
  roles: string[]
  users: string[]
  contact: { id: string; displayName: string }
  children: GroupResponse[] | null
}
