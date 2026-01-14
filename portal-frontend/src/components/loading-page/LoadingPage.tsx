import { ContentCard } from '@/components/content-card/ContentCard'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'

interface LoadingPageProps {
  title: string
  testId?: string
}
const LoadingPage = (props: LoadingPageProps) => {
  const { title, testId } = props
  return (
    <PageContainer headerType="onlyTitle" testId={testId}>
      <PageHeader title={title} />
      <PageBackground>
        <ContentCard className="p-10">
          <LoadingSpinner />
        </ContentCard>
      </PageBackground>
    </PageContainer>
  )
}

export default LoadingPage
