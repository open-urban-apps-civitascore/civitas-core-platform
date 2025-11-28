'use client'

import { ChevronDown, ChevronRight } from 'lucide-react'

interface PaletteCategoryProps {
  title: string
  isExpanded: boolean
  onToggle: () => void
  children: React.ReactNode
}

export const PaletteCategory: React.FC<PaletteCategoryProps> = ({ title, isExpanded, onToggle, children }) => {
  return (
    <div className="border-b border-gray-200">
      {/* Category Header */}
      <button onClick={onToggle} className="w-full flex items-center gap-2 px-3 py-2 hover:bg-gray-50 text-left">
        {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        <span className="font-medium text-sm text-gray-700">{title}</span>
      </button>

      {/* Category Content */}
      {isExpanded && children}
    </div>
  )
}
