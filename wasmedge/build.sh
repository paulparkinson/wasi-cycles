#!/bin/bash

# WasmEdge TxEventQ podman Image Build Script
set -e

IMAGE_NAME="wasicycles-wasmedge-txeventq"

echo "🐳 Building WasmEdge TxEventQ podman Image with Podman..."
echo "📦 Image: $IMAGE_NAME"
echo "� Runtime: WasmEdge WASM"
echo "🔗 Integration: Oracle TxEventQ + HTTPS"
echo ""

# Build the podman image using Podman
echo "🚀 Starting podman build..."
podman build -t "$IMAGE_NAME" .

echo ""
echo "✅ Build complete!"
echo "� Image: $IMAGE_NAME"
echo "🏃 Run with: ./run.sh"
echo "� Or build and run: ./build_and_run.sh"
echo ""
echo "🔍 Verify build:"
echo "  podman images | grep $IMAGE_NAME"
