'use client'

import { useParams } from 'next/navigation'

import { RoleDetails } from '../components/RoleDetails'

const EditRolePage = () => {
  const params = useParams<{ roleId: string }>()
  const { roleId } = params

  return <RoleDetails roleId={roleId} />
}

export default EditRolePage
