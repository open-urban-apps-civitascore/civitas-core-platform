# Use Node.js Alpine for small image size and security
FROM node:22-alpine

WORKDIR /app

COPY package.json pnpm-lock.yaml ./
RUN npm install -g pnpm@latest && \
    pnpm install --frozen-lockfile --prod && \
    npm install -g concurrently json-server && \
    pnpm store prune

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

ENV PORT=80
EXPOSE 80
ENV NODE_ENV=production

# Temporary fix to run json-server alongside nextjs until proper backend is available
# Using concurrently to run both services and crash container if either service fails
CMD ["concurrently", "--kill-others", "--names", "JSON-SERVER,NEXT", "-c", "blue,green", "json-server json-server/db.json -p 3001", "pnpm:start"]
