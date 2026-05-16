#!/bin/bash
# Set up Let's Encrypt SSL for the given domain
# Usage: ./scripts/setup-ssl.sh your-domain.com your@email.com
set -euo pipefail
cd "$(dirname "$0")/.."

DOMAIN="${1:-}"
EMAIL="${2:-}"

if [ -z "$DOMAIN" ] || [ -z "$EMAIL" ]; then
    echo "Usage: $0 <domain> <email>"
    echo "Example: $0 zhizhou.example.com admin@example.com"
    exit 1
fi

echo "=== Requesting SSL certificate for $DOMAIN ==="
docker run --rm \
    -v "$(pwd)/certbot/conf:/etc/letsencrypt" \
    -v "$(pwd)/certbot/www:/var/www/certbot" \
    certbot/certbot certonly --webroot -w /var/www/certbot \
    -d "$DOMAIN" --email "$EMAIL" --agree-tos --non-interactive

if [ ! -f "certbot/conf/live/$DOMAIN/fullchain.pem" ]; then
    echo "ERROR: Certificate not found. Check certbot output above."
    exit 1
fi

echo ""
echo "=== Certificate obtained. Now update nginx.conf with SSL ==="
echo "1. Edit ../zhizhou_react/nginx.conf to add 443 server block"
echo "2. Run: docker compose up -d --build nginx"
echo "3. Verify: curl -I https://$DOMAIN"
echo ""
echo "=== Certificate will auto-renew monthly via /etc/cron.d/arknow-ssl ==="
