export type Permission = {
  id: string
  name: string
  description?: string
  permissionType: string
  category: string
}

export type PermissionCategory = {
  id: string
  title: string
}

export type PermissionItem = {
  name: Permission['name']
  value: Permission['id']
  category: PermissionCategory
}
