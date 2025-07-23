#!/bin/bash

# Load Oracle Cloud configuration from .env.local if available
if [ -f "../.env.local" ]; then
    echo "📥 Loading Oracle Cloud configuration from .env.local..."
    source "../.env.local"
elif [ -f ".env.local" ]; then
    echo "📥 Loading Oracle Cloud configuration from .env.local..."
    source ".env.local"
fi

# Default Oracle configuration (can be overridden by environment variables)
export ORACLE_USERNAME=${ORACLE_USERNAME:-"ADMIN"}
export ORACLE_PASSWORD=${ORACLE_PASSWORD:-"mypassword"}
export ORACLE_DB_NAME=${ORACLE_DB_NAME:-"MYDATABASE"}
export ORACLE_HOST=${ORACLE_HOST:-"myhost.adb.region.oraclecloudapps.com"}
export KAFKA_TOPIC=${KAFKA_TOPIC:-"TEST_KAFKA_TOPIC_NEW"}

# Default port configuration
export WASMTIME_PORT=${WASMTIME_PORT:-"8090"}

echo "🚀 Starting Wasmtime Server on port $WASMTIME_PORT..."
echo "📡 Server URL: http://localhost:$WASMTIME_PORT"
echo ""
echo "🏛️ Oracle Cloud Integration:"
echo "   ✅ Host: $ORACLE_HOST"
echo "   ✅ Database: $ORACLE_DB_NAME"
echo "   ✅ User: $ORACLE_USERNAME"
echo "   ✅ Topic: $KAFKA_TOPIC"
echo "   ✅ Topic: $KAFKA_TOPIC"
echo ""
echo "Available endpoints:"
echo "  GET  /                - Root information"
echo "  GET  /health          - Server health check"
echo "  POST /join            - Join the game"
echo "  POST /move            - Move a player"
echo "  POST /leave           - Leave the game"
echo "  GET  /players         - Get all players"
echo "  GET  /leaderboard     - Get the leaderboard"
echo "  GET  /consume-kafka   - Consume Kafka messages"
echo "  POST /test-kafka      - Test Kafka publishing"
echo ""
echo "Debug endpoints:"
echo "  GET  /debug/enable    - Enable verbose debug logging"
echo "  GET  /debug/disable   - Disable verbose debug logging"
echo "  GET  /debug/status    - Check debug logging status"
echo ""
echo "Press Ctrl+C to stop the server"
echo "----------------------------------------"

wasmtime serve -S cli --addr 0.0.0.0:$WASMTIME_PORT \
  --env ORACLE_HOST="$ORACLE_HOST" \
  --env ORACLE_USERNAME="$ORACLE_USERNAME" \
  --env ORACLE_PASSWORD="$ORACLE_PASSWORD" \
  --env ORACLE_DB_NAME="$ORACLE_DB_NAME" \
  --env KAFKA_TOPIC="$KAFKA_TOPIC" \
  target/wasm32-wasip2/release/http_server.wasm
