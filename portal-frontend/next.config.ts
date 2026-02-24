import { NextConfig } from 'next'
import createNextIntlPlugin from 'next-intl/plugin'

const nextConfig: NextConfig = {
  eslint: {
    // We run ESLint in CI, so we don't need to run it during production builds.
    ignoreDuringBuilds: true,
  },
  // Security headers are now handled by middleware for proper per-request nonce generation

  // pino uses thread-stream to spawn a worker with an absolute path resolved at build time.
  // Bundling pino/pino-pretty would embed the build-time path (e.g., /ROOT/node_modules/...)
  // which no longer exists at runtime. Marking them as external makes Next.js skip bundling
  // so the path is resolved at runtime instead.
  serverExternalPackages: ['pino', 'pino-pretty'],
}

const withNextIntl = createNextIntlPlugin()
export default withNextIntl(nextConfig)
