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
ORACLE_USERNAME=${ORACLE_USERNAME:-"ADMIN"}
ORACLE_PASSWORD=${ORACLE_PASSWORD:-"mypassword"}
ORACLE_DB_NAME=${ORACLE_DB_NAME:-"MYDATABASE"}
ORACLE_HOST=${ORACLE_HOST:-"myhost.adb.region.oraclecloudapps.com"}
KAFKA_TOPIC=${KAFKA_TOPIC:-"TEST_KAFKA_TOPIC_NEW"}

# Default port configuration
WASMER_PORT=${WASMER_PORT:-"8070"}

echo "🔥 Starting Wasmer Forge - WasiCycles Game Server"
echo "📦 Running Python in Wasmer WASM runtime"
echo "🌐 Server URL: http://localhost:$WASMER_PORT"
echo ""
echo "🏛️ Oracle Cloud Integration:"
echo "   ✅ Host: $ORACLE_HOST"
echo "   ✅ Database: $ORACLE_DB_NAME"
echo "   ✅ User: $ORACLE_USERNAME"
echo "   ✅ Topic: $KAFKA_TOPIC"
echo "   ✅ Kafka REST API"
echo "   ✅ ORDS API"
echo "   ✅ Leaderboard sync"
echo ""

wasmer run . --net \
  --env PORT=$WASMER_PORT \
  --env ORACLE_USERNAME="$ORACLE_USERNAME" \
  --env ORACLE_PASSWORD="$ORACLE_PASSWORD" \
  --env ORACLE_DB_NAME="$ORACLE_DB_NAME" \
  --env ORACLE_HOST="$ORACLE_HOST" \
  --env KAFKA_TOPIC="$KAFKA_TOPIC"