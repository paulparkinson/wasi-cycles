# WasmEdge WasiCycles Quantum Nexus - Ubuntu Runtime Container
FROM ubuntu:22.04 AS buildbase

# Install system dependencies and build tools
RUN apt-get update && apt-get install -y \
    curl \
    git \
    python3 \
    ca-certificates \
    bash \
    wget \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Install Rust
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
ENV PATH="/root/.cargo/bin:$PATH"

# Add wasm32-wasip1 target
RUN rustup target add wasm32-wasip1

# Install WasmEdge runtime
RUN curl -sSf https://raw.githubusercontent.com/WasmEdge/WasmEdge/master/utils/install.sh | bash -s -- --version=0.13.5
ENV PATH="/root/.wasmedge/bin:$PATH"

# Build stage
FROM buildbase AS build
WORKDIR /src

# Install clang and other build tools needed for ring crate compilation
RUN apt-get update && apt-get install -y clang build-essential

COPY . .

# Build the WASM binary with TxEventQ support
RUN RUSTFLAGS="--cfg wasmedge --cfg tokio_unstable" cargo build --target wasm32-wasip1 --release

# Runtime stage
FROM ubuntu:22.04 AS runtime

# Add metadata labels
LABEL org.opencontainers.image.title="WasiCycles WasmEdge Quantum Nexus"
LABEL org.opencontainers.image.description="WasmEdge-powered HTTPS server for WasiCycles multiplayer snake game with TxEventQ integration"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.vendor="Oracle Labs"

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    curl \
    git \
    python3 \
    ca-certificates \
    bash \
    && rm -rf /var/lib/apt/lists/*

# Install WasmEdge runtime
RUN curl -sSf https://raw.githubusercontent.com/WasmEdge/WasmEdge/master/utils/install.sh | bash -s -- --version=0.13.5
ENV PATH="/root/.wasmedge/bin:$PATH"

# Set up working directory and copy the built WASM file
WORKDIR /app
COPY --from=build /src/target/wasm32-wasip1/release/wasmedge_server_and_client_https.wasm ./

# Expose port 8080 for WasmEdge HTTPS Server
EXPOSE 8080

# Set environment variables for TxEventQ integration
ENV PORT=8080
ENV RUST_LOG=info

# Create a wrapper script that passes environment variables to WasmEdge
RUN echo '#!/bin/bash\n\
echo "🚀 Starting WasmEdge WasiCycles Quantum Nexus..."\n\
echo "🔐 HTTPS ENABLED: Using proven wasmedge_hyper_demo approach!"\n\
echo "🎮 WasiCycles Game Server on port 8080"\n\
echo "🔗 TxEventQ integration ready!"\n\
echo "----------------------------------------"\n\
exec wasmedge \\\n\
  --env ORACLE_USERNAME="${ORACLE_USERNAME:-ADMIN}" \\\n\
  --env ORACLE_PASSWORD="${ORACLE_PASSWORD:-mypassword}" \\\n\
  --env ORACLE_DB_NAME="${ORACLE_DB_NAME:-MYDATABASE}" \\\n\
  --env ORACLE_HOST="${ORACLE_HOST:-myhost.adb.region.oraclecloudapps.com}" \\\n\
  --env KAFKA_TOPIC="${KAFKA_TOPIC:-WASICYCLES_GAME_EVENTS}" \\\n\
  wasmedge_server_and_client_https.wasm\n\
' > /app/start.sh && chmod +x /app/start.sh

# Run the WasmEdge binary using our wrapper script
ENTRYPOINT ["/app/start.sh"]
