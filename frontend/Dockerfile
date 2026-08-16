# ---- build ----
FROM node:22-alpine AS build
WORKDIR /build

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- runtime ----
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
# Files under templates/ are run through envsubst at startup and written to
# /etc/nginx/conf.d/ — native behaviour of the official image.
COPY default.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80
