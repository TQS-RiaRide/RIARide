# Keycloak with the RiaRide login theme: the frontend's own components, packed by Keycloakify
# (frontend/src/keycloak-theme). Built from the frontend folder, see infra/compose.yaml.

FROM node:24-alpine AS theme
# Keycloakify packs the theme jar with Maven.
RUN apk add --no-cache maven openjdk21-jdk
WORKDIR /app
COPY package.json package-lock.json ./
# No dependency needs an install script, so none is allowed to run.
RUN npm ci --ignore-scripts
COPY . .
RUN npm run build:keycloak-theme

FROM quay.io/keycloak/keycloak:26.8.0
COPY --from=theme /app/dist_keycloak/riaride-keycloak-theme.jar /opt/keycloak/providers/
# Keycloak's own unprivileged user, as in the base image.
USER 1000
