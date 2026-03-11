'use client'

import { JSX } from 'react'

import type { DiagramSession } from '../../types/session'
import { UMLCanvas } from '../canvas/UMLCanvas'

interface TabContentProps {
  session: DiagramSession
  isActive: boolean
  placeHolder?: JSX.Element
}

export const TabContent: React.FC<TabContentProps> = props => {
  const { isActive, placeHolder } = props
  if (!isActive) {
    return null // Don't render inactive tabs to improve performance
  }

  return (
    <div className="flex-1 h-full">
      <UMLCanvas className="flex-1" placeHolder={placeHolder} />
    </div>
  )
}
