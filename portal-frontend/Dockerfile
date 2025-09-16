# Use Node.js Alpine for small size and security
FROM node:22-alpine

# Set working directory
WORKDIR /app

# Copy package files for production dependencies
COPY package.json pnpm-lock.yaml ./

# Install pnpm globally and install production dependencies only
RUN npm install -g pnpm@latest && \
    pnpm install --frozen-lockfile --prod && \
    pnpm store prune

# Copy pre-built application from CI artifacts
COPY .next ./.next
COPY public ./public

# Create non-root user for security
RUN addgroup -g 1001 -S nodejs && \
    adduser -S nextjs -u 1001 && \
    chown -R nextjs:nodejs /app

# Switch to non-root user
USER nextjs

# Set PORT to 80 for production container (can be overridden)
ENV PORT=80

# Expose port 80 for production
EXPOSE 80

# Set environment to production
ENV NODE_ENV=production

# Start the Next.js application
CMD ["pnpm", "start"]
