import { Info, LucideIcon, TriangleAlert } from 'lucide-react'

interface BaseTextBoxProps {
  text: string
  icon?: LucideIcon
}

export const BaseTextBox = (props: BaseTextBoxProps) => {
  const { text, icon } = props
  const Icon = icon
  return (
    <div className="flex items-center gap-2 bg-background border border-border rounded-lg px-4 py-2 text-sm font-medium">
      {Icon && <Icon className="h-4 w-4 shrink-0" />}
      <span>{text}</span>
    </div>
  )
}

export const InfoBox = ({ text }: Omit<BaseTextBoxProps, 'icon'>) => <BaseTextBox text={text} icon={Info} />

export const AlertBox = ({ text }: Omit<BaseTextBoxProps, 'icon'>) => <BaseTextBox text={text} icon={TriangleAlert} />
