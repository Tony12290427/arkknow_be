#!/bin/bash
# Run this on a fresh Ubuntu 22.04 ECS instance
set -euo pipefail

echo "=== Installing dependencies ==="
apt update && apt upgrade -y
apt install -y docker.io docker-compose-v2 curl

echo "=== Configuring Docker ==="
systemctl enable docker
usermod -aG docker "$(whoami)"

echo "=== Setting vm.max_map_count for Elasticsearch ==="
sysctl -w vm.max_map_count=262144
echo 'vm.max_map_count=262144' >> /etc/sysctl.conf

echo "=== Creating 2G swap ==="
if [ ! -f /swapfile ]; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "=== Setting up daily database backup to /opt/backups ==="
mkdir -p /opt/backups
cat > /etc/cron.d/arknow-backup <<'CRON'
0 3 * * * root docker exec arknow-mysql mysqldump -uroot -p${MYSQL_ROOT_PASSWORD} --all-databases | gzip > /opt/backups/arknow-$(date +\%Y\%m\%d).sql.gz && find /opt/backups -mtime +7 -delete
CRON

echo "=== Setting up monthly SSL renewal ==="
cat > /etc/cron.d/arknow-ssl <<'CRON'
0 3 1 * * root docker run --rm -v /opt/arknow_be/certbot/conf:/etc/letsencrypt -v /opt/arknow_be/certbot/www:/var/www/certbot certbot/certbot renew --quiet && docker exec arknow-nginx nginx -s reload
CRON

echo "=== Done. Reboot or re-login for docker group to take effect. ==="
