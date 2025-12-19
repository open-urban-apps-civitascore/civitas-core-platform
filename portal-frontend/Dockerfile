# Use Node.js Alpine for small image size and security
FROM node:22-alpine AS deps
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile --prod

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=80

COPY --from=deps /app/node_modules ./node_modules
COPY package.json ./package.json
COPY .next ./.next
COPY public ./public
COPY json-server ./json-server

# Create cache directory for Next.js image optimization
RUN mkdir -p .next/cache/images

# Create non-root user for security
RUN addgroup -g 1001 -S nodejs && \
    adduser -S nextjs -u 1001 && \
    chown -R nextjs:nodejs /app
USER nextjs

EXPOSE 80
ENV NODE_ENV=production

# Temporary fix to run json-server alongside nextjs until proper backend is available
# Using concurrently to run both services and crash container if either service fails
CMD ["concurrently", "--kill-others", "--names", "JSON-SERVER,NEXT", "-c", "blue,green", "json-server json-server/db.json -p 3001", "node_modules/.bin/next start"]
# CMD ["node_modules/.bin/next", "start"]
