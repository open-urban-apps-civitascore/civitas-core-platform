import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'

import { LinkCell } from '../link-cell/LinkCell'

type ContactCellProps = {
  user:
    | {
        id: string
        name: string
      }
    | null
    | undefined
}

export const ContactCell = (props: ContactCellProps) => {
  const { user } = props

  if (!user) return null
  const userNameParts = user?.name.split(' ') || []
  const firstName = userNameParts[0] || ''
  const lastName = userNameParts[userNameParts.length - 1] || ''

  return (
    <LinkCell href={`/users/${user.id}`}>
      <div className="flex items-center gap-2">
        {firstName && lastName ? (
          <Avatar className="size-8 rounded-lg">
            <AvatarImage src="" alt={user?.name || ''} />
            <AvatarFallback className="rounded-lg">
              {`${firstName.charAt(0)}${lastName.charAt(0)}` || ''}
            </AvatarFallback>
          </Avatar>
        ) : null}

        {user?.name}
      </div>
    </LinkCell>
  )
}
