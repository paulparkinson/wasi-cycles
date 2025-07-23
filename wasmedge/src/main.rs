use serde_json::json;
use std::collections::HashMap;
use std::convert::Infallible;
use std::env;
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

use hyper::{Body, Client, Method, Request, Response, Server, Uri};
use hyper::service::{make_service_fn, service_fn};
use tokio::net::TcpListener;
use serde::{Deserialize, Serialize};
use base64::{Engine as _, engine::general_purpose::STANDARD as BASE64};
use hyper_rustls::HttpsConnectorBuilder;

type Result<T> = std::result::Result<T, Box<dyn std::error::Error + Send + Sync>>;

// Create HTTPS client for Oracle Cloud API calls
fn create_https_client() -> Client<hyper_rustls::HttpsConnector<hyper::client::HttpConnector>> {
    let https = HttpsConnectorBuilder::new()
        .with_webpki_roots()
        .https_or_http()
        .enable_http1()
        .build();
    Client::builder().build::<_, hyper::Body>(https)
}

// Game state structures
#[derive(Debug, Clone, Serialize, Deserialize)]
struct Player {
    id: String,
    x: f64,
    y: f64,
    direction: String,
    score: i32,
    color: String,
    alive: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct GameEvent {
    #[serde(rename = "type")]
    event_type: String,
    player_id: String,
    game_id: String,
    runtime: String,
    castle: String,
    timestamp: u64,
    data: serde_json::Value,
}

// Application state
type GameState = Arc<Mutex<HashMap<String, Player>>>;

// Oracle configuration
struct OracleConfig {
    kafka_url: String,
    ords_url: String,
    username: String,
    password: String,
    topic: String,
    host: String,
    db_name: String,
}

impl OracleConfig {
    fn from_env() -> Self {
        println!("🔍 Reading Oracle configuration from environment variables...");
        let host = env::var("ORACLE_HOST").unwrap_or_else(|e| {
            println!("⚠️ ORACLE_HOST not found ({}), using default", e);
            "myhost.adb.region.oraclecloudapps.com".to_string()
        });
        let db_name = env::var("ORACLE_DB_NAME").unwrap_or_else(|e| {
            println!("⚠️ ORACLE_DB_NAME not found ({}), using default", e);
            "MYDATABASE".to_string()
        });
        
        println!("✅ Oracle Host from env: {}", host);
        println!("✅ Oracle DB Name from env: {}", db_name);
        
        let topic = env::var("KAFKA_TOPIC").unwrap_or_else(|_| "TEST_KAFKA_TOPIC_NEW".to_string());
        println!("✅ Kafka Topic from env: {}", topic);
        
        Self {
            kafka_url: env::var("ORACLE_KAFKA_URL").unwrap_or_else(|_| 
                format!("https://{}/ords/admin/_/db-api/stable/database/txeventq/clusters/{}", host, db_name)
            ),
            ords_url: env::var("ORDS_URL").unwrap_or_else(|_| 
                format!("https://{}/ords/admin/_sdw", host)
            ),
            username: env::var("ORACLE_USERNAME").unwrap_or_else(|_| "ADMIN".to_string()),
            password: env::var("ORACLE_PASSWORD").unwrap_or_else(|_| "mypassword".to_string()),
            topic,
            host,
            db_name,
        }
    }
}

// Global application state
lazy_static::lazy_static! {
    static ref GAME_STATE: GameState = Arc::new(Mutex::new(HashMap::new()));
}

// Oracle configuration helper function (reads from env each time)
fn get_oracle_config() -> OracleConfig {
    OracleConfig::from_env()
}

#[tokio::main(flavor = "current_thread")]
async fn main() -> Result<()> {
    let addr = "0.0.0.0:8083";  // Different port for WasmEdge HTTPS
    
    println!("🚀 WasmEdge WasiCycles Quantum Nexus listening on {}", addr);
    println!("🎮 WasiCycles Game Server Endpoints:");
    println!("  GET  /                                           - Server info");
    println!("  GET  /health                                     - Health check");
    println!("  POST /join                                       - Join game");
    println!("  POST /move                                       - Move player");
    println!("  POST /leave                                      - Leave game");
    println!("  GET  /players                                    - Get all players");
    println!("  GET  /leaderboard                                - Get leaderboard");
    println!("  POST /test-kafka                                 - Test TxEventQ connectivity");
    println!("  GET  /consume-kafka                              - Consume messages");
    println!("  GET  /drain-messages                             - Drain all messages");
    println!("  POST /ai-action                                  - AI decision endpoint");
    println!("");
    println!("🔐 HTTPS SUPPORT: Oracle Cloud integration enabled!");
    let oracle_config = get_oracle_config();
    println!("🏛️ Oracle Kafka: {}", oracle_config.kafka_url);
    println!("🗃️ Oracle ORDS: {}", oracle_config.ords_url);
    println!("⚡ Quantum Nexus ready for interdimensional Snake battles!");

    let make_svc = make_service_fn(|_conn| async {
        Ok::<_, Infallible>(service_fn(handle_request))
    });

    let tcp_listener = TcpListener::bind(addr).await?;
    let server = Server::from_tcp(tcp_listener.into_std()?)?
        .serve(make_svc);

    server.await?;
    Ok(())
}

async fn handle_request(req: Request<Body>) -> std::result::Result<Response<Body>, Infallible> {
    let uri = req.uri();
    let path = uri.path();
    let method = req.method();

    let response = match (method, path) {
        (&Method::GET, "/") => server_info_response(),
        (&Method::GET, "/health") => health_response(),
        (&Method::POST, "/join") => join_response(req).await,
        (&Method::POST, "/move") => move_response(req).await,
        (&Method::POST, "/leave") => leave_response(req).await,
        (&Method::GET, "/players") => players_response(),
        (&Method::GET, "/leaderboard") => leaderboard_response().await,
        (&Method::POST, "/test-kafka") => test_kafka_response(req).await,
        (&Method::GET, "/consume-kafka") => consume_kafka_response().await,
        (&Method::GET, "/drain-messages") => drain_messages_response().await,
        (&Method::POST, "/ai-action") => ai_action_response(req).await,
        (&Method::OPTIONS, _) => cors_response(),
        _ => not_found_response(),
    };

    Ok(response)
}

fn server_info_response() -> Response<Body> {
    let info = json!({
        "message": "WasiCycles Quantum Nexus is running!",
        "runtime": "wasmedge",
        "castle": "Quantum Nexus",
        "color": "#9333ea",
        "endpoints": {
            "health": "/health",
            "join": "/join (POST)",
            "move": "/move (POST)",
            "leave": "/leave (POST)",
            "players": "/players",
            "leaderboard": "/leaderboard",
            "test-kafka": "/test-kafka (POST)",
            "consume-kafka": "/consume-kafka",
            "drain-messages": "/drain-messages",
            "ai-action": "/ai-action (POST)"
        },
        "integration": {
            "kafka": "Oracle Database Kafka API",
            "scores": "Oracle ORDS",
            "https": "enabled"
        },
        "timestamp": SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs()
    });

    Response::builder()
        .status(200)
        .header("content-type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(Body::from(info.to_string()))
        .unwrap()
}

fn health_response() -> Response<Body> {
    let players = GAME_STATE.lock().unwrap();
    let health = json!({
        "status": "healthy",
        "runtime": "wasmedge",
        "castle": "WASMEdge Cycle",
        "service": "WasiCycles WASMEdge Cycle",
        "version": "1.0.0",
        "color": "#9333ea",
        "active_players": players.len(),
        "oracle_integration": "enabled",
        "https_support": "enabled",
        "timestamp": SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs()
    });

    Response::builder()
        .status(200)
        .header("content-type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(Body::from(health.to_string()))
        .unwrap()
}

async fn join_response(req: Request<Body>) -> Response<Body> {
    let body_bytes = match hyper::body::to_bytes(req.into_body()).await {
        Ok(bytes) => bytes,
        Err(_) => return error_response("Failed to read request body", 400),
    };

    let payload: serde_json::Value = match serde_json::from_slice(&body_bytes) {
        Ok(payload) => payload,
        Err(_) => return error_response("Invalid JSON", 400),
    };

    let player_id = match payload["player_id"].as_str() {
        Some(id) => id.to_string(),
        None => return error_response("Missing player_id", 400),
    };

    let new_player = Player {
        id: player_id.clone(),
        x: 600.0, // Different starting position for WasmEdge
        y: 400.0,
        direction: "left".to_string(),
        score: 0,
        color: "#9333ea".to_string(), // Purple for WasmEdge
        alive: true,
    };

    // Add player to game state
    {
        let mut players = GAME_STATE.lock().unwrap();
        players.insert(player_id.clone(), new_player.clone());
    }

    // Create and publish join event to Oracle Kafka
    let join_event = GameEvent {
        event_type: "player_joined".to_string(),
        player_id: player_id.clone(),
        game_id: "wasicycles-multiplayer".to_string(),
        runtime: "wasmedge".to_string(),
        castle: "Quantum Nexus".to_string(),
        timestamp: SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs(),
        data: json!({
            "x": new_player.x,
            "y": new_player.y,
            "direction": new_player.direction,
            "color": new_player.color
        }),
    };

    tokio::spawn(async move {
        if let Err(e) = publish_to_oracle_kafka(&join_event).await {
            eprintln!("❌ Failed to publish join event: {}", e);
        }
    });

    let response = json!({
        "status": "success",
        "runtime": "wasmedge",
        "castle": "Quantum Nexus",
        "message": format!("Player {} joined Quantum Nexus", player_id),
        "player": new_player,
        "quantum_power": "activated",
        "oracle_integration": "active",
        "timestamp": SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs()
    });

    Response::builder()
        .status(200)
        .header("content-type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(Body::from(response.to_string()))
        .unwrap()
}

async fn move_response(req: Request<Body>) -> Response<Body> {
    let body_bytes = match hyper::body::to_bytes(req.into_body()).await {
        Ok(bytes) => bytes,
        Err(_) => return error_response("Failed to read request body", 400),
    };

    let payload: serde_json::Value = match serde_json::from_slice(&body_bytes) {
        Ok(payload) => payload,
        Err(_) => return error_response("Invalid JSON", 400),
    };

    let player_id = match payload["player_id"].as_str() {
        Some(id) => id.to_string(),
        None => return error_response("Missing player_id", 400),
    };

    let direction = match payload["direction"].as_str() {
        Some(dir) => dir.to_string(),
        None => return error_response("Missing direction", 400),
    };

    // Update player state
    let updated_player = {
        let mut players = GAME_STATE.lock().unwrap();
        let player = match players.get_mut(&player_id) {
            Some(p) => p,
            None => return error_response("Player not found", 404),
        };

        // Update direction
        player.direction = direction.clone();

        // Move player based on direction
        match direction.as_str() {
            "up" => player.y = (player.y - 20.0).max(0.0),
            "down" => player.y = (player.y + 20.0).min(600.0),
            "left" => player.x = (player.x - 20.0).max(0.0),
            "right" => player.x = (player.x + 20.0).min(800.0),
            _ => return error_response("Invalid direction", 400),
        }

        // Increment score for movement
        player.score += 1;

        player.clone()
    };

    // Create and publish move event to Oracle Kafka
    let move_event = GameEvent {
        event_type: "player_moved".to_string(),
        player_id: player_id.clone(),
        game_id: "wasicycles-multiplayer".to_string(),
        runtime: "wasmedge".to_string(),
        castle: "Quantum Nexus".to_string(),
        timestamp: SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs(),
        data: json!({
            "x": updated_player.x,
            "y": updated_player.y,
            "direction": updated_player.direction,
            "score": updated_player.score
        }),
    };

    // Spawn async tasks for Oracle operations
    let player_id_clone = player_id.clone();
    let score = updated_player.score;
    tokio::spawn(async move {
        if let Err(e) = publish_to_oracle_kafka(&move_event).await {
            eprintln!("❌ Failed to publish move event: {}", e);
        }
    });

    tokio::spawn(async move {
        if let Err(e) = update_player_score_ords(&player_id_clone, score, "wasicycles-multiplayer").await {
            eprintln!("❌ Failed to update score: {}", e);
        }
    });

    let response = json!({
        "status": "success",
        "runtime": "wasmedge",
        "castle": "Quantum Nexus",
        "message": format!("Player {} moved {} in Quantum Nexus", player_id, direction),
        "player": updated_player,
        "quantum_power": "flowing",
        "oracle_integration": "active",
        "timestamp": SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs()
    });

    Response::builder()
        .status(200)
        .header("content-type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(Body::from(response.to_string()))
        .unwrap()
}

async fn leave_response(req: Request<Body>) -> Response<Body> {
    let body_bytes = match hyper::body::to_bytes(req.into_body()).await {
        Ok(bytes) => bytes,
        Err(_) => return error_response("Failed to read request body", 400),
    };

    let payload: serde_json::Value = match serde_json::from_slice(&body_bytes) {
        Ok(payload) => payload,
        Err(_) => return error_response("Invalid JSON", 400),
    };

    let player_id = match payload["player_id"].as_str() {
        Some(id) => id.to_string(),
        None => return error_response("Missing player_id", 400),
    };

    // Remove player from game state
    let removed_player = {
        let mut players = GAME_STATE.lock().unwrap();
        players.remove(&player_id)
    };

    if let Some(player) = removed_player {
        // Create and publish leave event to Oracle Kafka
        let leave_event = GameEvent {
            event_type: "player_left".to_string(),
            player_id: player_id.clone(),
            game_id: "wasicycles-multiplayer".to_string(),
            runtime: "wasmedge".to_string(),
            castle: "Quantum Nexus".to_string(),
            timestamp: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_secs(),
            data: json!({
                "final_score": player.score
            }),
        };

        tokio::spawn(async move {
            if let Err(e) = publish_to_oracle_kafka(&leave_event).await {
                eprintln!("❌ Failed to publish leave event: {}", e);
            }
        });

        let response = json!({
            "status": "success",
            "runtime": "wasmedge",
            "castle": "Quantum Nexus",
            "message": format!("Player {} left Quantum Nexus", player_id),
            "final_score": player.score,
            "quantum_status": "disconnected",
            "timestamp": SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_secs()
        });

        Response::builder()
            .status(200)
            .header("content-type", "application/json")
            .header("Access-Control-Allow-Origin", "*")
            .body(Body::from(response.to_string()))
            .unwrap()
    } else {
        error_response("Player not found", 404)
    }
}

fn players_response() -> Response<Body> {
    let players = GAME_STATE.lock().unwrap();
    let players_vec: Vec<&Player> = players.values().collect();
    
    let response = json!({
        "runtime": "wasmedge",
        "castle": "Quantum Nexus",
        "players": players_vec,
        "count": players_vec.len(),
        "timestamp": SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs()
    });

    Response::builder()
        .status(200)
        .header("content-type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(Body::from(response.to_string()))
        .unwrap()
}

async fn leaderboard_response() -> Response<Body> {
    match get_leaderboard_ords().await {
        Ok(leaderboard) => {
            let response = json!({
                "runtime": "wasmedge",
                "castle": "Quantum Nexus",
                "leaderboard": leaderboard,
                "source": "Oracle ORDS",
                "timestamp": SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .unwrap()
                    .as_secs()
            });

            Response::builder()
                .status(200)
                .header("content-type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .body(Body::from(response.to_string()))
                .unwrap()
        }
        Err(e) => {
            eprintln!("❌ Failed to get leaderboard: {}", e);
            error_response("Failed to get leaderboard", 500)
        }
    }
}

fn cors_response() -> Response<Body> {
    Response::builder()
        .status(204)
        .header("Access-Control-Allow-Origin", "*")
        .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        .header("Access-Control-Allow-Headers", "Content-Type")
        .body(Body::empty())
        .unwrap()
}

fn not_found_response() -> Response<Body> {
    let error = json!({
        "error": "Endpoint not found",
        "runtime": "wasmedge",
        "castle": "Quantum Nexus",
        "available_endpoints": ["/", "/health", "/join", "/move", "/leave", "/players", "/leaderboard"],
        "note": "Use POST for game commands"
    });

    Response::builder()
        .status(404)
        .header("content-type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(Body::from(error.to_string()))
        .unwrap()
}

fn error_response(message: &str, status: u16) -> Response<Body> {
    let error = json!({
        "error": message,
        "runtime": "wasmedge",
        "castle": "Quantum Nexus",
        "status": status
    });

    Response::builder()
        .status(status)
        .header("content-type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(Body::from(error.to_string()))
        .unwrap()
}

// Oracle TxEventQ topic creation
async fn create_txeventq_topic(topic_name: &str) -> Result<bool> {
    let oracle_config = get_oracle_config();
    let auth = format!("{}:{}", oracle_config.username, oracle_config.password);
    let auth_header = format!("Basic {}", BASE64.encode(auth));

    // Topic creation endpoint: /{schema}/_/db-api/stable/database/txeventq/clusters/{cluster}/topics
    let cluster_name = &oracle_config.db_name;
    let base_url = format!("https://{}/ords/admin", oracle_config.host);
    let url = format!("{}/_/db-api/stable/database/txeventq/clusters/{}/topics", base_url, cluster_name);

    let topic_payload = json!({
        "topic_name": topic_name,
        "partitions_count": "1"
    });

    let uri: Uri = url.parse()?;
    let client = create_https_client();

    let req = Request::builder()
        .method("POST")
        .uri(uri)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .header("Authorization", &auth_header)
        .body(Body::from(topic_payload.to_string()))?;

    let response = client.request(req).await?;
    let status = response.status();
    let body_bytes = hyper::body::to_bytes(response.into_body()).await?;
    let response_text = String::from_utf8(body_bytes.to_vec())?;

    if status.is_success() {
        println!("✅ TxEventQ topic '{}' created successfully from WasmEdge: {}", topic_name, response_text);
        Ok(true)
    } else if status == 400 && response_text.to_lowercase().contains("already exists") {
        println!("ℹ️ Topic '{}' already exists - WasmEdge", topic_name);
        Ok(true)
    } else {
        eprintln!("❌ Failed to create topic '{}' from WasmEdge: {} - {}", topic_name, status, response_text);
        Ok(false)
    }
}

// Oracle Kafka integration
async fn publish_to_oracle_kafka(event: &GameEvent) -> Result<()> {
    let oracle_config = get_oracle_config();
    // Ensure topic exists (create if needed)
    if let Err(e) = create_txeventq_topic(&oracle_config.topic).await {
        eprintln!("⚠️ Topic creation failed, attempting to publish anyway: {}", e);
    }

    // Oracle TxEventQ REST API format (key difference: value must be JSON string, not object)
    let kafka_payload = json!({
        "records": [{
            "key": format!("wasmedge-{}", SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_secs()),
            "value": serde_json::to_string(event)?  // JSON string, not object!
        }]
    });

    let auth = format!("{}:{}", oracle_config.username, oracle_config.password);
    let auth_header = format!("Basic {}", BASE64.encode(auth));

    // Use correct TxEventQ endpoint format (base URL, not cluster URL)
    let base_url = format!("https://{}/ords/admin", oracle_config.host);
    let url = format!("{}/_/db-api/stable/database/txeventq/topics/{}", 
        base_url, 
        oracle_config.topic
    );

    let uri: Uri = url.parse()?;
    let client = create_https_client();
    
    let req = Request::builder()
        .method("POST")
        .uri(uri)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .header("Authorization", &auth_header)
        .body(Body::from(kafka_payload.to_string()))?;

    let response = client.request(req).await?;

    if response.status().is_success() {
        let body_bytes = hyper::body::to_bytes(response.into_body()).await?;
        let response_text = String::from_utf8(body_bytes.to_vec())?;
        println!("✅ TxEventQ message published from WasmEdge: {} for player {} - Response: {}", 
                event.event_type, event.player_id, response_text);
    } else {
        let status = response.status();
        let body_bytes = hyper::body::to_bytes(response.into_body()).await?;
        let error_text = String::from_utf8(body_bytes.to_vec())?;
        eprintln!("❌ Oracle TxEventQ error from WasmEdge: {} - {}", status, error_text);
    }

    Ok(())
}

// Oracle ORDS integration
async fn update_player_score_ords(
    player_id: &str,
    score: i32,
    game_id: &str,
) -> Result<()> {
    let oracle_config = get_oracle_config();
    let score_data = json!({
        "player_id": player_id,
        "score": score,
        "game_id": game_id,
        "runtime": "wasmedge",
        "castle": "Quantum Nexus",
        "timestamp": SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs()
    });

    let auth = format!("{}:{}", oracle_config.username, oracle_config.password);
    let auth_header = format!("Basic {}", BASE64.encode(auth));

    let url = format!("{}/scores/", oracle_config.ords_url);
    let uri: Uri = url.parse()?;
    let client = create_https_client();

    let req = Request::builder()
        .method("POST")
        .uri(uri)
        .header("Content-Type", "application/json")
        .header("Authorization", &auth_header)
        .body(Body::from(score_data.to_string()))?;

    let response = client.request(req).await?;

    if response.status().is_success() {
        println!("✅ Updated score via ORDS: {} for player {}", score, player_id);
    } else {
        eprintln!("❌ ORDS score update error: {}", response.status());
    }

    Ok(())
}

async fn get_leaderboard_ords() -> Result<serde_json::Value> {
    let oracle_config = get_oracle_config();
    let auth = format!("{}:{}", oracle_config.username, oracle_config.password);
    let auth_header = format!("Basic {}", BASE64.encode(auth));

    let url = format!("{}/leaderboard/", oracle_config.ords_url);
    let uri: Uri = url.parse()?;
    let client = create_https_client();

    let req = Request::builder()
        .method("GET")
        .uri(uri)
        .header("Accept", "application/json")
        .header("Authorization", &auth_header)
        .body(Body::empty())?;

    let response = client.request(req).await?;

    if response.status().is_success() {
        let body_bytes = hyper::body::to_bytes(response.into_body()).await?;
        let json_value: serde_json::Value = serde_json::from_slice(&body_bytes)?;
        Ok(json_value)
    } else {
        Err(format!("ORDS leaderboard error: {}", response.status()).into())
    }
}

async fn test_kafka_response(req: Request<Body>) -> Response<Body> {
    // Parse request body
    let body = match hyper::body::to_bytes(req.into_body()).await {
        Ok(bytes) => bytes,
        Err(_) => {
            return Response::builder()
                .status(400)
                .header("Content-Type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .body(Body::from(json!({
                    "error": "Failed to read request body",
                    "runtime": "wasmedge",
                    "castle": "Quantum Nexus"
                }).to_string()))
                .unwrap();
        }
    };

    let test_data: serde_json::Value = match serde_json::from_slice(&body) {
        Ok(data) => data,
        Err(_) => json!({"test_message": "default-test"})
    };

    let test_message = test_data.get("test_message")
        .and_then(|v| v.as_str())
        .unwrap_or("default-test");

    // Create test event
    let test_event = GameEvent {
        event_type: "test_connectivity".to_string(),
        player_id: "wasmedge-test-player".to_string(),
        game_id: "connectivity-test".to_string(),
        runtime: "wasmedge".to_string(),
        castle: "Quantum Nexus".to_string(),
        timestamp: SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs(),
        data: json!({
            "test_message": test_message,
            "connectivity_test": true
        })
    };

    // Publish test message to TxEventQ
    let kafka_result = match publish_to_oracle_kafka(&test_event).await {
        Ok(_) => json!({
            "success": true,
            "message": "Test message published to TxEventQ successfully"
        }),
        Err(e) => json!({
            "success": false,
            "error": e.to_string()
        })
    };

    let response_data = json!({
        "status": "success",
        "runtime": "wasmedge",
        "castle": "Quantum Nexus",
        "test_message_sent": test_message,
        "kafka_result": kafka_result,
        "timestamp": SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs()
    });

    Response::builder()
        .status(200)
        .header("Content-Type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(Body::from(response_data.to_string()))
        .unwrap()
}

async fn consume_kafka_response() -> Response<Body> {
    // Consume messages from Oracle TxEventQ
    let messages = consume_from_oracle_kafka().await;
    
    let response_data = json!({
        "status": "success",
        "runtime": "wasmedge",
        "castle": "Quantum Nexus",
        "endpoint": "consume_kafka",
        "messages": messages,
        "count": messages.len(),
        "timestamp": SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs()
    });

    Response::builder()
        .status(200)
        .header("Content-Type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(Body::from(response_data.to_string()))
        .unwrap()
}

async fn drain_messages_response() -> Response<Body> {
    // Drain all messages from Oracle TxEventQ for this runtime
    let mut all_messages = Vec::new();
    let mut batch_count = 0;
    let max_batches = 10;  // Prevent infinite loop

    // Keep consuming until no more messages or max batches reached
    loop {
        if batch_count >= max_batches {
            break;
        }
        
        let messages = consume_from_oracle_kafka().await;
        
        // Check if we got any actual messages (not just empty/error responses)
        let has_actual_messages = messages.iter().any(|msg| {
            !msg.get("error").is_some() && 
            !msg.get("message").map(|m| m.as_str().unwrap_or("").contains("No messages available")).unwrap_or(false)
        });
        
        if !has_actual_messages {
            break;
        }
        
        all_messages.extend(messages);
        batch_count += 1;
        
        // Small delay between batches to avoid overwhelming the server
        tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
    }

    let response_data = json!({
        "status": "success",
        "runtime": "wasmedge",
        "castle": "Quantum Nexus",
        "endpoint": "drain_messages",
        "messages_drained": all_messages,
        "total_count": all_messages.len(),
        "batches_processed": batch_count,
        "timestamp": SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs()
    });

    Response::builder()
        .status(200)
        .header("Content-Type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(Body::from(response_data.to_string()))
        .unwrap()
}

async fn consume_from_oracle_kafka() -> Vec<serde_json::Value> {
    let oracle_config = get_oracle_config();
    let client = create_https_client();
    
    // Oracle TxEventQ consumer setup using topic-specific consumer group
    let topic_safe = oracle_config.topic.to_lowercase().replace("_", "");
    let consumer_group_id = format!("wasmedge_{}_grp", topic_safe);
    let txeventq_base_url = format!("https://{}/ords/admin/_/db-api/stable/database/txeventq", oracle_config.host);
    let cluster_name = &oracle_config.db_name;
    
    println!("🔍 Topic: {}, Consumer Group: {}", oracle_config.topic, consumer_group_id);
    
    // Create Basic Auth header
    let auth_string = format!("{}:{}", oracle_config.username, oracle_config.password);
    let encoded_auth = BASE64.encode(auth_string.as_bytes());
    
    // Step 1: Create consumer group using correct URL pattern
    let consumer_group_url = format!("{}/clusters/{}/consumer-groups/{}", 
        txeventq_base_url, cluster_name, consumer_group_id);
    let group_payload = json!({
        "topic_name": oracle_config.topic
    });
    
    if let Ok(uri) = consumer_group_url.parse::<Uri>() {
        let req = Request::builder()
            .method(Method::POST)
            .uri(uri)
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Basic {}", encoded_auth))
            .body(Body::from(group_payload.to_string()));
            
        if let Ok(req) = req {
            match client.request(req).await {
                Ok(response) => {
                    let status = response.status();
                    if let Ok(body_bytes) = hyper::body::to_bytes(response.into_body()).await {
                        let response_text = String::from_utf8_lossy(&body_bytes);
                        if status.is_success() {
                            println!("✅ Consumer group created: {}", response_text);
                        } else if status == 409 || response_text.to_lowercase().contains("already exists") {
                            println!("ℹ️ Consumer group already exists: {}", consumer_group_id);
                        } else {
                            println!("⚠️ Consumer group creation returned: {} - {}", status, response_text);
                            println!("🔍 Debug: Consumer group URL: {}", consumer_group_url);
                            println!("🔍 Debug: Consumer group payload: {}", group_payload);
                            println!("ℹ️ Continuing with consumer instance creation...");
                        }
                    }
                }
                Err(e) => println!("❌ Consumer group request failed: {}", e),
            }
        }
    }
    
    // Step 2: Create consumer instance using correct pattern (POST /consumers/{group})
    let consumer_instance_url = format!("{}/consumers/{}", 
        txeventq_base_url, consumer_group_id);
    let consumer_payload = json!({});  // Empty payload for consumer creation
    
    let consumer_instance_id = if let Ok(uri) = consumer_instance_url.parse::<Uri>() {
        let req = Request::builder()
            .method(Method::POST)
            .uri(uri)
            .header("Content-Type", "application/json")
            .header("Authorization", format!("Basic {}", encoded_auth))
            .body(Body::from(consumer_payload.to_string()));
            
        if let Ok(req) = req {
            match client.request(req).await {
                Ok(response) => {
                    let status = response.status();
                    if let Ok(body_bytes) = hyper::body::to_bytes(response.into_body()).await {
                        let response_text = String::from_utf8_lossy(&body_bytes);
                        println!("🔧 Consumer instance response: {}", response_text);
                        
                        if status.is_success() {
                            if let Ok(consumer_data) = serde_json::from_str::<serde_json::Value>(&response_text) {
                                let instance_id = consumer_data.get("instance_id")
                                    .and_then(|v| v.as_str())
                                    .unwrap_or("unknown_instance")
                                    .to_string();
                                println!("✅ Consumer instance created: {}", instance_id);
                                instance_id
                            } else {
                                println!("❌ Failed to parse consumer response: {}", response_text);
                                "unknown_instance".to_string()
                            }
                        } else {
                            println!("❌ Consumer instance creation failed: {} - {}", status, response_text);
                            "unknown_instance".to_string()
                        }
                    } else {
                        "unknown_instance".to_string()
                    }
                }
                Err(e) => {
                    println!("❌ Consumer instance request failed: {}", e);
                    "unknown_instance".to_string()
                }
            }
        } else {
            "unknown_instance".to_string()
        }
    } else {
        "unknown_instance".to_string()
    };
    
    // Step 3: Consume records using correct pattern: /consumers/{group}/instances/{instance_id}/records
    let consume_url = format!("{}/consumers/{}/instances/{}/records", 
        txeventq_base_url, consumer_group_id, consumer_instance_id);
    
    println!("🔍 Consuming from URL: {}", consume_url);
    println!("🔍 Debug: Consumer group: {}", consumer_group_id);
    println!("🔍 Debug: Consumer instance: {}", consumer_instance_id);
    println!("🔍 Debug: Topic: {}", oracle_config.topic);
    
    if let Ok(uri) = consume_url.parse::<Uri>() {
        let req = Request::builder()
            .method(Method::GET)
            .uri(uri)
            .header("Accept", "application/json")
            .header("Authorization", format!("Basic {}", encoded_auth))
            .body(Body::empty());
            
        if let Ok(req) = req {
            match client.request(req).await {
                Ok(response) => {
                    let status = response.status();
                    if let Ok(body_bytes) = hyper::body::to_bytes(response.into_body()).await {
                        let response_text = String::from_utf8_lossy(&body_bytes);
                        
                        println!("✅ TxEventQ consumer response ({}): {}", status, response_text);
                        
                        if response_text.trim() == "[]" {
                            println!("🔍 Debug: Empty response - no messages available in topic or consumer offset is at end");
                        }
                        
                        if status.is_success() {
                            if !response_text.trim().is_empty() && response_text != "[]" {
                                match serde_json::from_str::<Vec<serde_json::Value>>(&response_text) {
                                    Ok(records) => {
                                        let mut processed_messages = Vec::new();
                                        
                                        for record in records {
                                            let processed_msg = json!({
                                                "topic": record.get("topic").unwrap_or(&json!(oracle_config.topic)),
                                                "partition": record.get("partition").unwrap_or(&json!(0)),
                                                "offset": record.get("offset").unwrap_or(&json!("unknown")),
                                                "timestamp": record.get("timestamp").unwrap_or(&json!(SystemTime::now()
                                                    .duration_since(UNIX_EPOCH)
                                                    .unwrap()
                                                    .as_secs())),
                                                "key": record.get("key"),
                                                "data": parse_message_value(&record),
                                                "consumed_by": "wasmedge",
                                                "consumed_at": SystemTime::now()
                                                    .duration_since(UNIX_EPOCH)
                                                    .unwrap()
                                                    .as_secs(),
                                                "instance_id": consumer_instance_id.clone()
                                            });
                                            processed_messages.push(processed_msg);
                                        }
                                        
                                        return processed_messages;
                                    }
                                    Err(e) => {
                                        println!("❌ Failed to parse records: {}", e);
                                        return vec![json!({
                                            "error": format!("Parse error: {}", e),
                                            "raw_response": response_text,
                                            "consumed_by": "wasmedge",
                                            "consumed_at": SystemTime::now()
                                                .duration_since(UNIX_EPOCH)
                                                .unwrap()
                                                .as_secs()
                                        })];
                                    }
                                }
                            } else {
                                return vec![json!({
                                    "message": "No messages available (empty response)",
                                    "consumed_by": "wasmedge",
                                    "consumed_at": SystemTime::now()
                                        .duration_since(UNIX_EPOCH)
                                        .unwrap()
                                        .as_secs()
                                })];
                            }
                        } else {
                            return vec![json!({
                                "error": format!("HTTP {}: {}", status, response_text),
                                "consumed_by": "wasmedge",
                                "consumed_at": SystemTime::now()
                                    .duration_since(UNIX_EPOCH)
                                    .unwrap()
                                    .as_secs()
                            })];
                        }
                    }
                }
                Err(e) => {
                    println!("❌ Consume request failed: {}", e);
                    return vec![json!({
                        "error": format!("Request failed: {}", e),
                        "consumed_by": "wasmedge",
                        "consumed_at": SystemTime::now()
                            .duration_since(UNIX_EPOCH)
                            .unwrap()
                            .as_secs()
                    })];
                }
            }
        }
    }
    
    // Return error if we reach here
    vec![json!({
        "error": "Consumer setup failed",
        "consumed_by": "wasmedge", 
        "consumed_at": SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs()
    })]
}

fn parse_message_value(msg: &serde_json::Value) -> serde_json::Value {
    if let Some(value) = msg.get("value") {
        if let Some(value_str) = value.as_str() {
            // Try to parse JSON string
            match serde_json::from_str(value_str) {
                Ok(parsed) => parsed,
                Err(_) => value.clone(),
            }
        } else {
            value.clone()
        }
    } else {
        msg.clone()
    }
}

async fn ai_action_response(req: Request<Body>) -> Response<Body> {
    let body_bytes = match hyper::body::to_bytes(req.into_body()).await {
        Ok(bytes) => bytes,
        Err(_) => return error_response("Failed to read request body", 400),
    };

    let game_data: serde_json::Value = match serde_json::from_slice(&body_bytes) {
        Ok(data) => data,
        Err(e) => {
            eprintln!("❌ Failed to parse AI request: {}", e);
            return error_response("Invalid JSON", 400);
        }
    };

    // WasmEdge AI: Strategic territory controller  
    let action = get_wasmedge_ai_action(&game_data);
    
    let response = json!({
        "runtime": "wasmedge",
        "castle": "Quantum Nexus",
        "action": action,
        "timestamp": SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_millis()
    });

    Response::builder()
        .status(200)
        .header("Content-Type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        .header("Access-Control-Allow-Headers", "Content-Type")
        .body(Body::from(response.to_string()))
        .unwrap()
}

fn get_wasmedge_ai_action(game_data: &serde_json::Value) -> serde_json::Value {
    // WasmEdge Quantum Nexus AI: Strategic territory controller
    // Strategy: Control center, build defensive walls, expand territory
    
    // Create owned bindings for empty maps to ensure they live long enough
    let empty_arena = serde_json::Map::new();
    let empty_players = serde_json::Map::new();
    let empty_trails = serde_json::Map::new();
    let empty_position = serde_json::Map::new();
    
    let arena = game_data.get("arena").and_then(|a| a.as_object()).unwrap_or(&empty_arena);
    let arena_size = arena.get("size").and_then(|s| s.as_u64()).unwrap_or(20) as f64;
    let players = game_data.get("players").and_then(|p| p.as_object()).unwrap_or(&empty_players);
    let trails = game_data.get("trails").and_then(|t| t.as_object()).unwrap_or(&empty_trails);
    
    let my_player_id = "ai-wasmedge";
    
    // Find my player data
    let my_player = players.get(my_player_id).and_then(|p| p.as_object());
    if my_player.is_none() {
        return json!({
            "type": "turn",
            "direction": "forward",
            "reason": "initializing"
        });
    }
    
    let my_player = my_player.unwrap();
    let my_pos = my_player.get("position").and_then(|p| p.as_object()).unwrap_or(&empty_position);
    let my_x = my_pos.get("x").and_then(|x| x.as_f64()).unwrap_or(0.0);
    let my_z = my_pos.get("z").and_then(|z| z.as_f64()).unwrap_or(0.0);
    let my_direction = my_player.get("direction").and_then(|d| d.as_u64()).unwrap_or(0) as i32;
    
    // Check immediate danger ahead
    let (next_x, next_z) = calculate_next_position(my_x, my_z, my_direction);
    if is_position_dangerous(next_x, next_z, trails, arena_size) {
        // Immediate danger - choose safest turn
        let left_dir = (my_direction - 1 + 4) % 4;
        let right_dir = (my_direction + 1) % 4;
        
        let (left_x, left_z) = calculate_next_position(my_x, my_z, left_dir);
        let (right_x, right_z) = calculate_next_position(my_x, my_z, right_dir);
        
        let left_safe = !is_position_dangerous(left_x, left_z, trails, arena_size);
        let right_safe = !is_position_dangerous(right_x, right_z, trails, arena_size);
        
        if left_safe && right_safe {
            // Both safe - choose based on territory strategy
            let center_x = arena_size / 2.0;
            let center_z = arena_size / 2.0;
            
            let left_to_center = ((left_x - center_x).powi(2) + (left_z - center_z).powi(2)).sqrt();
            let right_to_center = ((right_x - center_x).powi(2) + (right_z - center_z).powi(2)).sqrt();
            
            // Prefer staying near center for territory control
            if left_to_center < right_to_center {
                return json!({
                    "type": "turn",
                    "direction": "left",
                    "reason": "avoid_and_control_territory_left"
                });
            } else {
                return json!({
                    "type": "turn",
                    "direction": "right",
                    "reason": "avoid_and_control_territory_right"
                });
            }
        } else if left_safe {
            return json!({
                "type": "turn",
                "direction": "left", 
                "reason": "avoid_collision_left_only"
            });
        } else if right_safe {
            return json!({
                "type": "turn",
                "direction": "right",
                "reason": "avoid_collision_right_only"
            });
        } else {
            return json!({
                "type": "turn",
                "direction": "left",
                "reason": "desperate_turn"
            });
        }
    }
    
    // No immediate danger - execute territory strategy
    let center_x = arena_size / 2.0;
    let center_z = arena_size / 2.0;
    let distance_to_center = ((my_x - center_x).powi(2) + (my_z - center_z).powi(2)).sqrt();
    
    // If far from center, try to move toward it
    if distance_to_center > arena_size / 4.0 {
        let dx = center_x - my_x;
        let dz = center_z - my_z;
        
        let target_direction = if dx.abs() > dz.abs() {
            if dx > 0.0 { 1 } else { 3 }  // East or West
        } else {
            if dz < 0.0 { 0 } else { 2 }  // North or South
        };
        
        if target_direction != my_direction {
            let turn_diff = (target_direction - my_direction + 4) % 4;
            if turn_diff == 1 {
                return json!({
                    "type": "turn",
                    "direction": "right",
                    "reason": "moving_to_center_right"
                });
            } else if turn_diff == 3 {
                return json!({
                    "type": "turn", 
                    "direction": "left",
                    "reason": "moving_to_center_left"
                });
            }
        }
    }
    
    // Near center - look for strategic wall building opportunities
    // Check if we're creating a good defensive pattern
    let steps_ahead = calculate_safe_distance(my_x, my_z, my_direction, trails, arena_size);
    
    if steps_ahead < 8 {
        // Look for a turn that creates more territory
        let left_dir = (my_direction - 1 + 4) % 4;
        let right_dir = (my_direction + 1) % 4;
        
        let left_distance = calculate_safe_distance(my_x, my_z, left_dir, trails, arena_size);
        let right_distance = calculate_safe_distance(my_x, my_z, right_dir, trails, arena_size);
        
        if left_distance > steps_ahead + 2 {
            return json!({
                "type": "turn",
                "direction": "left",
                "reason": "strategic_territory_expansion_left"
            });
        } else if right_distance > steps_ahead + 2 {
            return json!({
                "type": "turn",
                "direction": "right", 
                "reason": "strategic_territory_expansion_right"
            });
        }
    }
    
    // Default: continue forward building walls
    json!({
        "type": "move",
        "direction": "forward",
        "reason": "building_defensive_wall"
    })
}

fn calculate_next_position(x: f64, z: f64, direction: i32) -> (f64, f64) {
    match direction {
        0 => (x, z - 1.0),  // North
        1 => (x + 1.0, z),  // East
        2 => (x, z + 1.0),  // South
        3 => (x - 1.0, z),  // West
        _ => (x, z)
    }
}

fn is_position_dangerous(x: f64, z: f64, trails: &serde_json::Map<String, serde_json::Value>, arena_size: f64) -> bool {
    // Check arena bounds
    if x < 0.0 || x >= arena_size || z < 0.0 || z >= arena_size {
        return true;
    }
    
    // Check trail collisions
    let pos_key = format!("{},{}", x as i32, z as i32);
    for (_, trail_positions) in trails.iter() {
        if let Some(positions) = trail_positions.as_array() {
            for pos in positions {
                if let Some(pos_str) = pos.as_str() {
                    if pos_str == pos_key {
                        return true;
                    }
                }
            }
        }
    }
    
    false
}

fn calculate_safe_distance(x: f64, z: f64, direction: i32, trails: &serde_json::Map<String, serde_json::Value>, arena_size: f64) -> i32 {
    let mut distance = 0;
    let mut current_x = x;
    let mut current_z = z;
    
    for _ in 0..(arena_size as i32 * 2) {
        let (next_x, next_z) = calculate_next_position(current_x, current_z, direction);
        if is_position_dangerous(next_x, next_z, trails, arena_size) {
            break;
        }
        distance += 1;
        current_x = next_x;
        current_z = next_z;
    }
    
    distance
}
