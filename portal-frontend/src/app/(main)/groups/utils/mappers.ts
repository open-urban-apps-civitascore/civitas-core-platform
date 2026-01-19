import { Group, GroupBaseFormData } from '@/types/groups'
import { Contact } from '@/types/users'

export const mapFormGroupToApiData = (formData: GroupBaseFormData, groupData: Group, contact: Contact | null) => {
  const group: Group = {
    ...formData,
    contact: contact ? { id: contact.id, displayName: contact.displayName } : null,
    parent: groupData.parent,
    roles: groupData.roles,
    subgroups: groupData.subgroups,
    users: groupData.users,
    dataspace: groupData.dataspace,
  }
  return group
}
