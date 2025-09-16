import { Building2 } from 'lucide-react'
import React from 'react'

interface OrganizationInfoProps {
  organizationName: string
  tenant: string
  icon?: React.ReactNode
}

export const OrganizationInfo = (props: OrganizationInfoProps) => {
  const { organizationName, tenant, icon } = props

  return (
    <div className="flex items-center justify-start space-x-2">
      <div className="size-8 rounded-lg  flex items-center justify-center inverted-colors: bg-transparent">
        {icon || <Building2 strokeWidth="1.5" />}
      </div>
      <div className="grid flex-1 text-left text-sm leading-tight">
        <span className="truncate font-medium">{organizationName}</span>
        <span className="truncate font-small text-neutral-600">{tenant}</span>
      </div>
    </div>
  )
}
