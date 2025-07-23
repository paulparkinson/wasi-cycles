#!/bin/bash

# WasiCycles Game Frontend Server
set -e

echo "🎮 Starting WasiCycles Game Frontend..."
echo "🌐 Port: 8001"
echo ""

# Kill any existing frontend server process
echo "🧹 Cleaning up existing processes..."
pkill -f "node server.js" 2>/dev/null || echo "No existing server found"

# Wait a moment for cleanup
sleep 2

echo "🚀 Starting frontend server with cache-busting headers..."
echo "🔗 Game will be available at: http://localhost:8001"
echo "📱 Features:"
echo "   - No-cache headers for development"
echo "   - Three.js WebXR-compatible game"
echo "   - Cross-runtime multiplayer support"
echo ""

# Run the node server
node server.js

