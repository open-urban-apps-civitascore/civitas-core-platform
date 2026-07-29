import { NextConfig } from 'next'
import createNextIntlPlugin from 'next-intl/plugin'

const nextConfig: NextConfig = {
  eslint: {
    // We run ESLint in CI, so we don't need to run it during production builds.
    ignoreDuringBuilds: true,
  },
  // Security headers (CSP etc.) are set in middleware for proper per-request nonce generation.
  // Cache-Control: no-store is emitted automatically by Next.js for dynamic routes in production,
  // so no headers() entry is needed here.

  // pino uses thread-stream to spawn a worker with an absolute path resolved at build time.
  // Bundling pino/pino-pretty would embed the build-time path (e.g., /ROOT/node_modules/...)
  // which no longer exists at runtime. Marking them as external makes Next.js skip bundling
  // so the path is resolved at runtime instead.
  serverExternalPackages: ['pino', 'pino-pretty'],
}

const withNextIntl = createNextIntlPlugin()
export default withNextIntl(nextConfig)
