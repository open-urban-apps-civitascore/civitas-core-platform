import { render, screen } from '@testing-library/react'
import { Globe, List } from 'lucide-react'

import { DatapoolsStateCard } from './DatapoolsStateCard'

describe('DatapoolsStateCard', () => {
  it('renders the title', () => {
    render(<DatapoolsStateCard icon={Globe} title="All datapools" subTitle="Any pipeline can use this datasource" />)
    expect(screen.getByText('All datapools')).toBeInTheDocument()
  })

  it('renders the subtitle', () => {
    render(<DatapoolsStateCard icon={Globe} title="All datapools" subTitle="Any pipeline can use this datasource" />)
    expect(screen.getByText('Any pipeline can use this datasource')).toBeInTheDocument()
  })

  it('renders an icon', () => {
    const { container } = render(
      <DatapoolsStateCard icon={Globe} title="All datapools" subTitle="Any pipeline can use this datasource" />,
    )
    expect(container.querySelector('svg')).toBeInTheDocument()
  })

  it('renders with a different icon', () => {
    const { container: containerGlobe } = render(<DatapoolsStateCard icon={Globe} title="title" subTitle="sub" />)
    const { container: containerList } = render(<DatapoolsStateCard icon={List} title="title" subTitle="sub" />)
    const globeSvg = containerGlobe.querySelector('svg')
    const listSvg = containerList.querySelector('svg')
    expect(globeSvg?.outerHTML).not.toEqual(listSvg?.outerHTML)
  })
})
