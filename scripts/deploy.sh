#!/bin/bash
# Full deployment: build images and start all services
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env.production ]; then
    echo "ERROR: .env.production not found."
    echo "Run: cp .env.production.example .env.production && edit it with real values"
    exit 1
fi

echo "=== Pulling latest code ==="
git pull origin main 2>/dev/null || echo "(not a git repo or no remote, skipping pull)"

echo "=== Building and starting all services ==="
docker compose --env-file .env.production up -d --build

echo ""
echo "=== Waiting for health checks ==="
sleep 10
docker compose ps

echo ""
echo "=== Health check ==="
curl -sf http://localhost:8080/actuator/health && echo "" || echo "WARNING: Backend health check failed"

echo ""
echo "=== Frontend check ==="
curl -sf http://localhost/ -o /dev/null && echo "Frontend: OK" || echo "WARNING: Frontend not reachable"

echo ""
echo "=== Deployment complete ==="
echo "Run ./scripts/setup-ssl.sh your-domain.com your@email.com to set up HTTPS"
