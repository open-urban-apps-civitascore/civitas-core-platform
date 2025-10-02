import { UserFormData, UserFormSchema } from "@/types/users"


export const saveUser = async (data: UserFormData) => {
  const parsed = UserFormSchema.parse(data)
  console.log('User gespeichert:', parsed)
}
