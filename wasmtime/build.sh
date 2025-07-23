#!/bin/bash

echo "🔨 Building Wasmtime server for wasm32-wasip2..."
echo "Target: wasm32-wasip2"
echo "Mode: Release"
echo ""

# Clear previous build output if exists for clean state checking
if [ -f "target/wasm32-wasip2/release/http_server.wasm" ]; then
    rm -f "target/wasm32-wasip2/release/http_server.wasm.build_in_progress"
    cp "target/wasm32-wasip2/release/http_server.wasm" "target/wasm32-wasip2/release/http_server.wasm.previous"
fi

# Run cargo build with detailed output
echo "Building Rust project..."
cargo build --release --target wasm32-wasip2
BUILD_RESULT=$?

# Check if build was successful by both exit code and file existence
if [ $BUILD_RESULT -eq 0 ] && [ -f "target/wasm32-wasip2/release/http_server.wasm" ]; then
    # Verify file was actually updated (not just using a previous build)
    if [ -f "target/wasm32-wasip2/release/http_server.wasm.previous" ]; then
        if cmp --silent "target/wasm32-wasip2/release/http_server.wasm" "target/wasm32-wasip2/release/http_server.wasm.previous"; then
            # Files are identical, warn but don't treat as failure
            echo ""
            echo "⚠️  Warning: WASM binary is identical to previous build"
            echo "   This could indicate the build didn't actually update the binary"
        fi
        # Clean up previous binary
        rm -f "target/wasm32-wasip2/release/http_server.wasm.previous"
    fi
    
    echo ""
    echo "✅ Build successful!"
    echo "📦 WASM binary: target/wasm32-wasip2/release/http_server.wasm"
    echo ""
    echo "Ready to run with: ./run.sh"
    exit 0
else
    echo ""
    echo "❌ Build failed"
    echo "Error code: $BUILD_RESULT"
    
    if [ ! -f "target/wasm32-wasip2/release/http_server.wasm" ]; then
        echo "WASM binary was not created"
    fi
    
    # Restore previous binary if it exists
    if [ -f "target/wasm32-wasip2/release/http_server.wasm.previous" ]; then
        echo "Restoring previous build"
        cp "target/wasm32-wasip2/release/http_server.wasm.previous" "target/wasm32-wasip2/release/http_server.wasm"
        rm -f "target/wasm32-wasip2/release/http_server.wasm.previous"
    fi
    
    exit 1
fi
