FROM nginx
RUN rm /etc/nginx/nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx-frontend.conf /etc/nginx/nginx.conf