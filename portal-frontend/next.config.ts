import { NextConfig } from 'next'
import createNextIntlPlugin from 'next-intl/plugin'

const nextConfig: NextConfig = {
  eslint: {
    // We run ESLint in CI, so we don't need to run it during production builds.
    ignoreDuringBuilds: true,
  },
}

const withNextIntl = createNextIntlPlugin()
export default withNextIntl(nextConfig)
