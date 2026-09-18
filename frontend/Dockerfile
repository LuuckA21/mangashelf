# ---- build ----
FROM node:22-alpine@sha256:c610fcdfb1d5b4740dd70c284ed3cb16bb857e0f7166196e36a5501df7a3aa32 AS build
WORKDIR /build

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- runtime ----
# 1.31.3 fixed CVE-2026-42533 in map/regex handling; stay on an explicit
# patched release rather than the mutable `alpine` tag.
FROM nginx:1.31.5-alpine@sha256:72ba65eb42c10344912a84ff42408db7d34f2feb642204570ab8fc5ffd29f1d3 AS runtime-base
# Update affected OS libraries without changing nginx or Alpine major versions.
RUN apk upgrade --no-cache libcrypto3 libssl3 libexpat libuuid

FROM runtime-base AS runtime
COPY --from=build /build/dist /usr/share/nginx/html
# Files under templates/ are run through envsubst at startup and written to
# /etc/nginx/conf.d/ — native behaviour of the official image.
COPY default.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80
