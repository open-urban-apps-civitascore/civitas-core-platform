export interface TitleSectionProps {
  title: string
  subtitle?: string
  className?: string
}

export const TitleSection = (props: TitleSectionProps) => {
  const { title, subtitle, className } = props

  return (
    <div className={className}>
      <h1 id="page-heading" className="my-1">
        {title}
      </h1>
      {subtitle && (
        <p id="page-subheading" className="text-primary-light">
          {subtitle}
        </p>
      )}
    </div>
  )
}
