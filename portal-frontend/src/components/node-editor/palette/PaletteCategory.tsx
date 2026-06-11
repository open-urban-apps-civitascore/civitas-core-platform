'use client'

import type { ReactNode } from 'react'

interface PaletteCategoryProps {
  label: string
  children: ReactNode
}

export const PaletteCategory = ({ label, children }: PaletteCategoryProps) => (
  <div className="space-y-1.5">
    <h4 className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</h4>
    <div className="space-y-1.5">{children}</div>
  </div>
)
