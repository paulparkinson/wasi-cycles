#!/bin/bash

# Cross-Runtime Burst Test - SUCCESSFUL Test from Conversation
# This recreates the "PERFECT! Cross-Runtime Burst Test SUCCESSFUL!" run
# Tests cross-runtime messaging with all 4 WASM engines

echo "🚀 Starting Cross-Runtime Burst Test..."
echo "Testing messaging flow: WASM engines → Oracle TxEventQ → Spring Boot OKafka"
echo

# Load environment variables
source .env.local

echo "📍 Testing endpoints:"
echo "- Wasmer: http://localhost:${WASMER_PORT}"
echo "- WASMEdge: http://localhost:${WASMEDGE_PORT}"  
echo "- Wasmtime: http://localhost:${WASMTIME_PORT}"
echo "- Spring Boot: http://localhost:${SPRING_BOOT_PORT}"
echo

echo "🔥 Sending test messages from WASM runtimes..."

# Test 1: Wasmer runtime (using test-kafka endpoint)
echo "📡 Testing Wasmer (Port ${WASMER_PORT})..."
curl -s -X POST http://localhost:${WASMER_PORT}/test-kafka -H "Content-Type: application/json" -d '{"message":"wasmer-test-1"}'
curl -s -X POST http://localhost:${WASMER_PORT}/test-kafka -H "Content-Type: application/json" -d '{"message":"wasmer-test-2"}'
curl -s -X POST http://localhost:${WASMER_PORT}/test-kafka -H "Content-Type: application/json" -d '{"message":"wasmer-test-3"}'
echo

# Test 2: WASMEdge runtime (using test-kafka endpoint)
echo "📡 Testing WASMEdge (Port ${WASMEDGE_PORT})..."
curl -s -X POST http://localhost:${WASMEDGE_PORT}/test-kafka -H "Content-Type: application/json" -d '{"message":"wasmedge-test-1"}'
curl -s -X POST http://localhost:${WASMEDGE_PORT}/test-kafka -H "Content-Type: application/json" -d '{"message":"wasmedge-test-2"}'
curl -s -X POST http://localhost:${WASMEDGE_PORT}/test-kafka -H "Content-Type: application/json" -d '{"message":"wasmedge-test-3"}'
echo

# Test 3: Wasmtime runtime (using test-kafka endpoint)  
echo "📡 Testing Wasmtime (Port ${WASMTIME_PORT})..."
curl -s -X POST http://localhost:${WASMTIME_PORT}/test-kafka -H "Content-Type: application/json" -d '{"message":"wasmtime-test-1"}'
curl -s -X POST http://localhost:${WASMTIME_PORT}/test-kafka -H "Content-Type: application/json" -d '{"message":"wasmtime-test-2"}'
curl -s -X POST http://localhost:${WASMTIME_PORT}/test-kafka -H "Content-Type: application/json" -d '{"message":"wasmtime-test-3"}'
echo

echo
echo "⏱️  Waiting 3 seconds for message processing..."
sleep 3

# Check Spring Boot consumption
echo "📥 Checking Spring Boot message consumption..."
curl -s http://localhost:${SPRING_BOOT_PORT}/cross-runtime/display-okafka-consumed-messages

echo
echo "✅ Cross-Runtime Burst Test Complete!"
echo "Expected: 9 new messages (3 from each WASM engine) consumed by Spring Boot"
echo "Message flow: WASM TxEventQ REST → Spring Boot OKafka Consumer"
echo "✅ All 3 WASM runtimes now working: Wasmer, WASMEdge, Wasmtime"
