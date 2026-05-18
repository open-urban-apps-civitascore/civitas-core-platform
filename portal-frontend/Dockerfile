# Use Node.js Alpine for small image size and security
FROM node:22-alpine AS deps
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile --prod

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=80

COPY --from=deps /app/node_modules ./node_modules
COPY package.json ./package.json
COPY .next ./.next
COPY public ./public

# Remove npm (unused at runtime) to eliminate bundled vulnerable deps (tar, glob, diff)
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx

# Create cache directory for Next.js image optimization
RUN mkdir -p .next/cache/images

# Create non-root user for security
RUN addgroup -g 1001 -S nodejs && \
    adduser -S nextjs -u 1001 && \
    chown -R nextjs:nodejs /app
USER nextjs

EXPOSE 80
ENV NODE_ENV=production

CMD ["node_modules/.bin/next", "start"]
