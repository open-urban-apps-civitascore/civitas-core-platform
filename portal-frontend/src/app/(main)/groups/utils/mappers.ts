import { GroupBaseFormData } from '@/types/groups'
import { Contact } from '@/types/users'

export const mapFormGroupToApiData = (formData: GroupBaseFormData, contact: Contact | null) => {
  const group = {
    ...formData,
    contactUser: contact?.id,
  }
  return group
}
