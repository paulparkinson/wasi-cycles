#!/bin/bash

#!/bin/bash

# Build script for WasiCycles Spring Boot Cross-Runtime Messaging Service
echo "🏭 Building WasiCycles Spring Boot Cross-Runtime Messaging Service..."

# Set the project directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

echo "📁 Working directory: $PROJECT_DIR"

# Source environment variables if .env.local exists
if [ -f "../.env.local" ]; then
    echo "📁 Loading environment variables from .env.local..."
    source ../.env.local
else
    echo "⚠️ No .env.local file found. Using default configuration."
fi

# Clean and build the project
echo "🧹 Cleaning previous build..."
mvn clean

echo "📦 Building the Spring Boot application..."
mvn package -DskipTests

# Check if build was successful
if [ $? -eq 0 ]; then
    echo "✅ Spring Boot Cross-Runtime Messaging Service built successfully!"
    echo "📋 Build artifacts:"
    ls -la target/*.jar

    echo ""
    echo "🚀 To run the application:"
    echo "   ./run.sh"
    echo ""
    echo "🔍 Available endpoints after startup:"
    echo "   Cross-Runtime Health: http://localhost:8050/cross-runtime/"
    echo "   OKafka Health: http://localhost:8050/okafka/"
    echo "   Cross-Runtime Test: http://localhost:8050/cross-runtime/test-all-combinations"
    echo "   Initialize Topic: http://localhost:8050/cross-runtime/initialize"
    echo ""
    echo "🏭 Supported OKafka-PLSQL Combinations:"
    echo "   1. OKafka enqueue → OKafka dequeue"
    echo "   2. OKafka enqueue → PLSQL dequeue"
    echo "   3. PLSQL enqueue → OKafka dequeue"
    echo "   4. PLSQL enqueue → PLSQL dequeue"
    echo "   OKafka: http://localhost:8050/okafka/"
    echo "   TxEventQ Messages: http://localhost:8050/txeventq/messages"
    echo "   TxEventQ Raw: http://localhost:8050/txeventq/messages/raw"
    echo "   Connection Info: http://localhost:8050/txeventq/connection-info"
else
    echo "❌ Build failed!"
    exit 1
fi
