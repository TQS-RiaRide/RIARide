# Build stage
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Keycloak as the browser reaches it (ADR 0009); Vite builds these into the bundle.
ARG VITE_KEYCLOAK_URL=http://localhost:8180
ARG VITE_KEYCLOAK_REALM=riaride
ARG VITE_KEYCLOAK_CLIENT_ID=riaride-spa
ENV VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL \
    VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM \
    VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID
RUN npm run build

# Runtime stage: static files served by nginx, /api forwarded to the backend
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
