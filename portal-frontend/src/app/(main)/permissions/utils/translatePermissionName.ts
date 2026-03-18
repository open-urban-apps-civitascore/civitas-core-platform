type TranslationFn = {
  (key: string, params?: Record<string, string>): string
  has: (key: string) => boolean
}

export const translatePermissionName = (item: string, t: TranslationFn): string => {
  const lastUnderscoreIndex = item.lastIndexOf('_')
  if (lastUnderscoreIndex === -1) return item

  const permission = item.substring(0, lastUnderscoreIndex)
  const action = item.substring(lastUnderscoreIndex + 1).toLowerCase()

  const permissionKey = `permissions.values.${permission}` as const
  const actionKey = `permissions.actions.${action}` as const

  if (!t.has(permissionKey) || !t.has(actionKey)) {
    return item
  }

  return t('permissions.systemPermissions.entry', {
    permission: t(permissionKey),
    action: t(actionKey),
  })
}
