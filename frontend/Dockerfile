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
# I file in templates/ vengono passati a envsubst all'avvio e scritti
# in /etc/nginx/conf.d/ (comportamento nativo dell'immagine ufficiale).
COPY default.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80
