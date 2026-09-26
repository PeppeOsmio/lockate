FROM node:22-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm install

COPY . .
RUN npm run build

FROM nginx:alpine AS app

COPY --from=builder /app/dist /usr/share/nginx/html
COPY public/config.json /etc/nginx/templates/config.json.template

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
