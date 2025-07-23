#!/bin/bash

# Stop WasmEdge Container
echo "🛑 Stopping WasmEdge WasiCycles Quantum Nexus Container..."
podman stop wasmedge-wasicycles || true
echo "✅ WasmEdge container stopped"
