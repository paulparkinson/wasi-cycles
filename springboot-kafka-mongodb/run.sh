#!/bin/bash

# Spring Boot Cross-Runtime Messaging - Run Script

# Load Oracle Cloud configuration from .env.local if available
if [ -f "../.env.local" ]; then
    echo "� Loading Oracle Cloud configuration from .env.local..."
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
ORACLE_TNS_ADMIN=${ORACLE_TNS_ADMIN:-"/path/to/wallet"}
ORACLE_TNS_ALIAS=${ORACLE_TNS_ALIAS:-"mydatabase"}

# Default port configuration
SPRING_BOOT_PORT=${SPRING_BOOT_PORT:-"8050"}
KAFKA_TOPIC=${KAFKA_TOPIC:-"WASICYCLES_GAME_EVENTS"}
ORDS_URL=${ORDS_URL:-"https://myhost.adb.region.oraclecloudapps.com/ords/admin/_sdw"}

echo "🏭 Starting Spring Boot Cross-Runtime Messaging Factory"
echo "� Running Spring Boot OKafka + PLSQL integration"
echo "🌐 Server URL: http://localhost:8050"
echo ""
echo "🏛️ Oracle Cloud Integration:"
echo "   ✅ Host: $ORACLE_HOST"
echo "   ✅ Database: $ORACLE_DB_NAME"
echo "   ✅ User: $ORACLE_USERNAME"
echo "   ✅ TNS Admin: $ORACLE_TNS_ADMIN"
echo "   ✅ TNS Alias: ${ORACLE_TNS_ALIAS}_high"
echo "   ✅ Topic: $KAFKA_TOPIC"
echo "   ✅ ORDS URL: $ORDS_URL"
echo "   ✅ Port: $SPRING_BOOT_PORT"
echo "   ✅ OKafka Native Client"
echo "   ✅ PLSQL TxEventQ Procedures"
echo ""
echo "� Supported Combinations:"
echo "   1️⃣ OKafka enqueue → OKafka dequeue"
echo "   2️⃣ OKafka enqueue → PLSQL dequeue" 
echo "   3️⃣ PLSQL enqueue → OKafka dequeue"
echo "   4️⃣ PLSQL enqueue → PLSQL dequeue"
echo ""

# Check if JAR file exists
JAR_FILE="target/springboot-kafka-mongodb-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR file not found: $JAR_FILE"
    echo "🔨 Please run ./build.sh first to build the application"
    exit 1
fi

java -jar "$JAR_FILE" --server.port=$SPRING_BOOT_PORT