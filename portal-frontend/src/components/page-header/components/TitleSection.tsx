
export interface TitleSectionProps {
  title: string
  subtitle?: string
}

export const TitleSection = (props: TitleSectionProps) => {
  const { title, subtitle } = props

  return (
    <div>
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
