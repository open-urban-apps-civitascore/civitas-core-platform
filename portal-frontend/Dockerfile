# Use Node.js Alpine for small image size and security
FROM node:22-alpine

WORKDIR /app

COPY package.json pnpm-lock.yaml ./
RUN npm install -g pnpm@latest && \
    pnpm install --frozen-lockfile --prod && \
    pnpm store prune

COPY .next ./.next
COPY public ./public

# Create non-root user for security
RUN addgroup -g 1001 -S nodejs && \
    adduser -S nextjs -u 1001 && \
    chown -R nextjs:nodejs /app
USER nextjs

ENV PORT=80
EXPOSE 80
ENV NODE_ENV=production

CMD ["pnpm", "start"]
