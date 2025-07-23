#!/bin/bash

# WasmEdge TxEventQ Container Runner Script
set -e

# Load Oracle Cloud configuration from .env.local if available
if [ -f "../.env.local" ]; then
    echo "📥 Loading Oracle Cloud configuration from .env.local..."
    source "../.env.local"
elif [ -f ".env.local" ]; then
    echo "📥 Loading Oracle Cloud configuration from .env.local..."
    source ".env.local"
fi

# Default Oracle configuration (can be overridden by environment variables)
ORACLE_USERNAME=${ORACLE_USERNAME:-"ADMIN"}
ORACLE_PASSWORD=${ORACLE_PASSWORD:-"mypassword"}
ORACLE_DB_NAME=${ORACLE_DB_NAME:-"MYDATABASE"}
ORACLE_HOST=${ORACLE_HOST:-"myhost.adb.region.oraclecloudapps.com"}
KAFKA_TOPIC=${KAFKA_TOPIC:-"TEST_KAFKA_TOPIC_NEW"}

# Default port configuration
WASMEDGE_PORT=${WASMEDGE_PORT:-"8083"}

IMAGE_NAME="wasicycles-wasmedge-txeventq"
CONTAINER_NAME="wasmedge-wasicycles"

echo "🚀 Starting WasmEdge WasiCycles Quantum Nexus Container..."
echo "📦 Image: $IMAGE_NAME"
echo "🌐 Port: $WASMEDGE_PORT"
echo "🏰 Container: $CONTAINER_NAME"
echo ""
echo "🏛️ Oracle Cloud Integration:"
echo "   ✅ Host: $ORACLE_HOST"
echo "   ✅ Database: $ORACLE_DB_NAME"
echo "   ✅ User: $ORACLE_USERNAME"
echo "   ✅ Topic: $KAFKA_TOPIC"
echo ""

# Stop existing container if running
if podman ps -q --filter "name=$CONTAINER_NAME" | grep -q .; then
    echo "🛑 Stopping existing container..."
    podman stop "$CONTAINER_NAME"
    podman rm "$CONTAINER_NAME"
fi

# Run the container with podman and environment variables
echo "🚀 Starting container..."
podman run --rm -d \
    --name "$CONTAINER_NAME" \
    -p $WASMEDGE_PORT:8083 \
    -e ORACLE_USERNAME="$ORACLE_USERNAME" \
    -e ORACLE_PASSWORD="$ORACLE_PASSWORD" \
    -e ORACLE_DB_NAME="$ORACLE_DB_NAME" \
    -e ORACLE_HOST="$ORACLE_HOST" \
    -e KAFKA_TOPIC="$KAFKA_TOPIC" \
    "$IMAGE_NAME"

echo ""
echo "✅ Container started successfully!"
echo "🌐 Access at: http://localhost:$WASMEDGE_PORT"
echo "📊 Container: $CONTAINER_NAME"
echo ""
echo "🎮 Available Endpoints:"
echo "  GET  http://localhost:$WASMEDGE_PORT/         - Server info"
echo "  GET  http://localhost:$WASMEDGE_PORT/health   - Health check"
echo "  POST http://localhost:$WASMEDGE_PORT/test-kafka - Test TxEventQ"
echo ""
echo "🔍 Monitor with:"
echo "  podman logs $CONTAINER_NAME"
echo "  podman ps"
echo ""
echo "🛑 Stop with:"
echo "  podman stop $CONTAINER_NAME"
