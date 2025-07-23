#!/bin/bash

# WasmEdge Container Log Viewer
# View real-time logs from the WasmEdge WasiCycles container

CONTAINER_NAME="wasmedge-wasicycles"

echo "🔍 Viewing logs for WasmEdge WasiCycles container..."
echo "📦 Container: $CONTAINER_NAME"
echo "🏰 Castle: Quantum Nexus"
echo "⚡ Runtime: WasmEdge"
echo ""
echo "Press Ctrl+C to stop viewing logs"
echo "----------------------------------------"

# Check if container exists and is running
if ! podman ps --format "{{.Names}}" | grep -q "^${CONTAINER_NAME}$"; then
    echo "❌ Container '$CONTAINER_NAME' is not running"
    echo ""
    echo "Available containers:"
    podman ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    echo ""
    echo "To start the container, run: ./run.sh"
    exit 1
fi

echo "✅ Container is running - showing logs..."
echo ""

# Follow logs with timestamps
podman logs -f --timestamps "$CONTAINER_NAME"
