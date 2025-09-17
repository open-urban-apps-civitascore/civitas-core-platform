import { cva, type VariantProps } from 'class-variance-authority'
import * as React from 'react'

import { Badge, BadgeProps, badgeVariants } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

// extends only variants, base classes are kept
const extendedBadgeVariants = cva(badgeVariants(), {
  variants: {
    variant: {
      success: 'border-transparent bg-success text-secondary-foreground [a&]:hover:bg-success/90',
      warn: 'border-transparent bg-warn text-secondary-foreground [a&]:hover:bg-warn/90',
      error: 'border-transparent bg-error text-secondary-foreground [a&]:hover:bg-error/90',
    },
  },
})

export type BaseBadgeProps = Omit<BadgeProps, 'variant'> & VariantProps<typeof extendedBadgeVariants>

export const BaseBadge = ({ className, variant, ...props }: BaseBadgeProps) => {
  return <Badge className={cn(extendedBadgeVariants({ variant }), className)} {...props} />
}
