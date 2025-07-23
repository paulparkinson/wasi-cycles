use waki::{handler, ErrorCode, Request, Response, Client, Method};
use std::collections::HashMap;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

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
    position: Option<Position>,
    direction: Option<String>,
    score: Option<i32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct Position {
    x: f64,
    y: f64,
}

// Oracle configuration from environment variables
fn get_oracle_host() -> String {
    std::env::var("ORACLE_HOST").unwrap_or_else(|_| 
        "myhost.adb.region.oraclecloudapps.com".to_string()
    )
}

fn get_oracle_db_name() -> String {
    std::env::var("ORACLE_DB_NAME").unwrap_or_else(|_| 
        "MYDATABASE".to_string()
    )
}

fn get_oracle_base_url() -> String {
    format!("https://{}/ords/admin", get_oracle_host())
}

fn get_ords_url() -> String {
    format!("https://{}/ords/admin/_sdw", get_oracle_host())
}

fn get_oracle_user() -> String {
    std::env::var("ORACLE_USERNAME").unwrap_or_else(|_| "ADMIN".to_string())
}

fn get_oracle_password() -> String {
    std::env::var("ORACLE_PASSWORD").unwrap_or_else(|_| "mypassword".to_string())
}

fn get_txeventq_base_url() -> String {
    format!("{}/_/db-api/stable/database/txeventq", get_oracle_base_url())
}

fn get_kafka_topic() -> String {
    std::env::var("KAFKA_TOPIC").unwrap_or_else(|_| "TEST_KAFKA_TOPIC_NEW".to_string())
}

const LEADERBOARD_TOPIC: &str = "WASICYCLES_LEADERBOARD";

// Cache for created topics
static mut CREATED_TOPICS: Option<std::collections::HashSet<String>> = None;

// Static game state (simplified for WASM)
static mut GAME_STATE: Option<HashMap<String, Player>> = None;
static mut LAST_SAVED_STATE: Option<String> = None;
// Flag to control log verbosity
static mut DEBUG_LOGGING: bool = false;
// Global request counter to track requests across handler invocations
static mut GLOBAL_REQUEST_COUNTER: u64 = 0;
// Last state reconstruction time to prevent excessive calls
static mut LAST_RECONSTRUCTION_TIME: u64 = 0;

// Helper function to safely access DEBUG_LOGGING
fn is_debug_enabled() -> bool {
    unsafe { DEBUG_LOGGING }
}

// Serialize and deserialize game state for persistence across requests
fn save_game_state(players: &mut HashMap<String, Player>) -> Result<(), ErrorCode> {
    unsafe {
        // Minimal logging - only if debug enabled or non-empty state
        let player_count = players.len();
        if player_count > 0 {
            eprintln!("[INFO] Saving game state with {} players", player_count);
        }
        
        // Serialize game state to JSON
        let serialized = match serde_json::to_string(players) {
            Ok(s) => s,
            Err(e) => {
                eprintln!("[ERROR] Failed to serialize game state: {:?}", e);
                return Err(ErrorCode::InternalError(None));
            }
        };
        
        // Store serialized state in static variable (may get reset in Wasmtime)
        LAST_SAVED_STATE = Some(serialized.clone());
        GAME_STATE = Some(players.clone());
        
        // Try multiple persistence mechanisms in order of preference
        let mut persistence_success = false;
        
        // 1. Try Oracle ORDS first (returns dummy values if fails, so always succeeds)
        if let Err(e) = save_state_to_oracle(&serialized) {
            eprintln!("[WARN] Failed to save state to Oracle: {}", e);
            
            // 2. Try TxEventQ snapshots as fallback (but avoid for empty states)
            if player_count > 0 {
                if let Err(kafka_err) = publish_state_snapshot_to_kafka(players) {
                    eprintln!("[WARN] Failed to publish state snapshot to TxEventQ: {}", kafka_err);
                } else {
                    persistence_success = true;
                    if is_debug_enabled() {
                        eprintln!("[DEBUG] State snapshot published to TxEventQ as fallback");
                    }
                }
            } else {
                eprintln!("[DEBUG] Skipping TxEventQ snapshot for empty state");
            }
        } else {
            persistence_success = true;
            if is_debug_enabled() {
                eprintln!("[DEBUG] State saved to Oracle successfully");
            }
        }
        
        if !persistence_success && player_count > 0 {
            eprintln!("[ERROR] All persistence mechanisms failed for {} players", player_count);
        }
        
        Ok(())
    }
}

// Save state to Oracle database as backup
fn save_state_to_oracle(state_json: &str) -> Result<(), String> {
    let client = Client::new();
    let state_data = json!({
        "state_key": "wasmtime_game_state",
        "state_data": state_json,
        "runtime": "wasmtime",
        "timestamp": get_timestamp()
    });

    let auth = base64_encode(&format!("{}:{}", get_oracle_user(), get_oracle_password()));
    let url = format!("{}/game_state/", get_ords_url());

    let response = client
        .post(&url)
        .headers([
            ("Content-Type", "application/json"),
            ("Authorization", &format!("Basic {}", auth))
        ])
        .body(state_data.to_string().as_bytes().to_vec())
        .send()
        .map_err(|e| format!("Oracle state save request failed: {}", e))?;

    let status_code = response.status_code();
    
    if status_code >= 200 && status_code < 300 {
        Ok(())
    } else {
        Err(format!("Oracle state save error: {}", status_code))
    }
}

// Load state from Oracle database
fn load_state_from_oracle() -> Result<String, String> {
    let client = Client::new();
    let auth = base64_encode(&format!("{}:{}", get_oracle_user(), get_oracle_password()));
    let url = format!("{}/game_state/wasmtime_game_state", get_ords_url());

    let response = client
        .get(&url)
        .headers([
            ("Accept", "application/json"),
            ("Authorization", &format!("Basic {}", auth))
        ])
        .send()
        .map_err(|e| format!("Oracle state load request failed: {}", e))?;

    let status_code = response.status_code();
    
    if status_code >= 200 && status_code < 300 {
        let body = response.body().map_err(|e| format!("Failed to read body: {}", e))?;
        let body_str = String::from_utf8(body).map_err(|e| format!("Invalid UTF-8: {}", e))?;
        
        // Parse the response to get the state_data field
        match serde_json::from_str::<serde_json::Value>(&body_str) {
            Ok(json_value) => {
                if let Some(state_data) = json_value.get("state_data") {
                    if let Some(state_str) = state_data.as_str() {
                        return Ok(state_str.to_string());
                    }
                }
                Err("State data not found in Oracle response".to_string())
            },
            Err(e) => Err(format!("Failed to parse Oracle response: {}", e))
        }
    } else if status_code == 404 {
        Err("No state found in Oracle".to_string())
    } else {
        Err(format!("Oracle state load error: {}", status_code))
    }
}

fn load_game_state() -> HashMap<String, Player> {
    unsafe {
        eprintln!("[INFO] Attempting to load game state");
        
        // First check if there's already a valid GAME_STATE and prefer that
        if let Some(game_state) = &GAME_STATE {
            if !game_state.is_empty() {
                let player_count = game_state.len();
                eprintln!("[INFO] Using existing in-memory game state with {} players", player_count);
                
                // Log player IDs for debugging
                if player_count > 0 {
                    let player_ids: Vec<&String> = game_state.keys().collect();
                    eprintln!("[INFO] Players in memory: {:?}", player_ids);
                }
                
                return game_state.clone();
            } else {
                eprintln!("[INFO] In-memory game state exists but is empty");
            }
        } else {
            eprintln!("[INFO] No in-memory game state found");
        }
        
        // Otherwise try to load from LAST_SAVED_STATE
        if let Some(serialized) = &LAST_SAVED_STATE {
            eprintln!("[INFO] Found serialized state, deserializing");
            
            match serde_json::from_str::<HashMap<String, Player>>(serialized) {
                Ok(state) => {
                    let count = state.len();
                    if count > 0 {
                        eprintln!("[INFO] Successfully loaded game state with {} players from serialized data", count);
                        // Log player IDs for debugging
                        let player_ids: Vec<&String> = state.keys().collect();
                        eprintln!("[INFO] Loaded players: {:?}", player_ids);
                        return state;
                    } else {
                        eprintln!("[INFO] Loaded empty game state from serialized data");
                    }
                }
                Err(e) => {
                    eprintln!("[ERROR] Failed to deserialize game state: {:?}", e);
                    eprintln!("[DEBUG] Failed serialized data: {}", serialized);
                }
            }
        } else {
            eprintln!("[INFO] No serialized state found");
        }
        
        // As a last resort, try to fetch from ORDS
        eprintln!("[INFO] Attempting to fetch player data from ORDS");
        match fetch_all_players_from_ords() {
            Ok(players_map) => {
                if !players_map.is_empty() {
                    eprintln!("[INFO] Successfully loaded {} players from ORDS", players_map.len());
                    return players_map;
                } else {
                    eprintln!("[INFO] No players found in ORDS database");
                }
            },
            Err(e) => {
                eprintln!("[ERROR] Failed to fetch players from ORDS: {}", e);
            }
        }
        
        // Return an empty HashMap if none exists or all retrieval methods failed
        eprintln!("[INFO] Creating new empty game state");
        HashMap::new()
    }
}

fn get_game_state() -> &'static mut HashMap<String, Player> {
    unsafe {
        // Always reload from LAST_SAVED_STATE to ensure persistence
        let mut new_state = HashMap::new();
        let mut state_source = "empty";
        
        // First try to load from LAST_SAVED_STATE (static variables - may be reset in Wasmtime)
        if let Some(serialized) = &LAST_SAVED_STATE {
            match serde_json::from_str::<HashMap<String, Player>>(serialized) {
                Ok(state) => {
                    new_state = state;
                    state_source = "static_variables";
                    // Only log when players actually exist
                    if !new_state.is_empty() {
                        eprintln!("[DEBUG] Loaded {} players from static variables", new_state.len());
                        let player_ids: Vec<String> = new_state.keys().cloned().collect();
                        eprintln!("[DEBUG] Loaded player IDs: {:?}", player_ids);
                    }
                },
                Err(e) => {
                    eprintln!("[ERROR] Failed to deserialize static state: {}", e);
                }
            }
        } 
        
        // If static variables are empty (Wasmtime reset), try Oracle backup first,
        // then try TxEventQ event sourcing as fallback
        if new_state.is_empty() {
            // Try Oracle backup first (returns empty state on failure, so it's safe)
            match load_state_from_oracle() {
                Ok(backup_state) => {
                    match serde_json::from_str::<HashMap<String, Player>>(&backup_state) {
                        Ok(state) => {
                            new_state = state;
                            state_source = "oracle_backup";
                            if !new_state.is_empty() {
                                eprintln!("[INFO] Restored {} players from Oracle backup due to static reset", new_state.len());
                                // Restore to static storage
                                LAST_SAVED_STATE = Some(backup_state);
                            }
                        },
                        Err(e) => {
                            eprintln!("[WARN] Failed to deserialize Oracle backup state: {}", e);
                            state_source = "empty_fallback";
                        }
                    }
                },
                Err(_) => {
                    // Oracle backup failed, try TxEventQ event sourcing
                    eprintln!("[INFO] Oracle backup unavailable, attempting TxEventQ event sourcing...");
                    let reconstructed_state = reconstruct_state_from_events();
                    if !reconstructed_state.is_empty() {
                        new_state = reconstructed_state;
                        state_source = "txeventq_events";
                        eprintln!("[INFO] Reconstructed {} players from TxEventQ events", new_state.len());
                        // Save the reconstructed state to static storage
                        match serde_json::to_string(&new_state) {
                            Ok(serialized) => {
                                LAST_SAVED_STATE = Some(serialized);
                            },
                            Err(e) => {
                                eprintln!("[ERROR] Failed to serialize reconstructed state: {}", e);
                            }
                        }
                    } else {
                        state_source = "empty_fallback";
                        if is_debug_enabled() {
                            eprintln!("[DEBUG] No events available for reconstruction, using empty state");
                        }
                    }
                }
            }
        }
        
        // Only log state source when we actually have players or when debugging
        if !new_state.is_empty() || is_debug_enabled() {
            eprintln!("[INFO] Game state loaded from: {} ({} players)", state_source, new_state.len());
        }
        
        // Update the global state
        GAME_STATE = Some(new_state);
        GAME_STATE.as_mut().unwrap()
    }
}

#[handler]
fn hello(req: Request) -> Result<Response, ErrorCode> {
    // Use the global counter to track requests across handler invocations
    unsafe {
        GLOBAL_REQUEST_COUNTER += 1;
        
        // Only log every 20 requests to reduce noise
        if GLOBAL_REQUEST_COUNTER % 20 == 0 {
            eprintln!("[INFO] Request handler processing request #{}", GLOBAL_REQUEST_COUNTER);
        }
    }
    
    let path = req.path();
    let method = req.method();
    
    // Add CORS headers for all responses
    let response_builder = Response::builder()
        .header("Access-Control-Allow-Origin", "*")
        .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        .header("Access-Control-Allow-Headers", "Content-Type");
    
    // Handle OPTIONS requests for CORS preflight
    match method {
        Method::Options => {
            return response_builder
                .status_code(200)
                .body("".to_string())
                .build();
        },
        _ => {}
    }
    
    // Process the request
    match (method, path) {
        // Special routes to toggle debug logging
        (Method::Get, "/debug/enable") => {
            unsafe { DEBUG_LOGGING = true; }
            eprintln!("[INFO] Debug logging enabled");
            response_builder
                .status_code(200)
                .header("Content-Type", "application/json")
                .body(r#"{"status": "success", "message": "Debug logging enabled"}"#.to_string())
                .build()
        },
        (Method::Get, "/debug/disable") => {
            unsafe { DEBUG_LOGGING = false; }
            eprintln!("[INFO] Debug logging disabled");
            response_builder
                .status_code(200)
                .header("Content-Type", "application/json")
                .body(r#"{"status": "success", "message": "Debug logging disabled"}"#.to_string())
                .build()
        },
        (Method::Get, "/debug/status") => {
            let status = is_debug_enabled();
            response_builder
                .status_code(200)
                .header("Content-Type", "application/json")
                .body(format!(r#"{{"status": "success", "debug_enabled": {}}}"#, status))
                .build()
        },
        (Method::Get, "/debug/wasmtime-behavior") => {
            let runtime_behavior = json!({
                "runtime": "wasmtime",
                "static_variable_persistence": "NOT_SUPPORTED",
                "issue": "Static variables reset between HTTP requests",
                "workarounds": [
                    "Oracle ORDS backup storage",
                    "TxEventQ event sourcing (event replay)",
                    "External state management"
                ],
                "comparison": {
                    "wasmtime": {
                        "statics_persist": false,
                        "reason": "New WASM instance per request (undocumented behavior)"
                    },
                    "wasmedge": {
                        "statics_persist": true,
                        "reason": "Reuses WASM instance across requests"
                    },
                    "wasmer": {
                        "statics_persist": "N/A",
                        "reason": "Uses Python for state management in this setup"
                    }
                },
                "current_solution": "Multi-tier fallback: static vars -> Oracle backup -> empty fallback",
                "documentation_status": "Behavior appears undocumented in official Wasmtime docs",
                "request_counter": unsafe { GLOBAL_REQUEST_COUNTER },
                "timestamp": get_timestamp()
            });

            response_builder
                .status_code(200)
                .header("Content-Type", "application/json")
                .body(runtime_behavior.to_string())
                .build()
        },
        (Method::Get, "/debug/reconstruct-from-events") => {
            eprintln!("[INFO] Manual TxEventQ state reconstruction triggered");
            let reconstructed_state = reconstruct_state_from_events();
            
            // Save reconstructed state if not empty
            unsafe {
                if !reconstructed_state.is_empty() {
                    if let Ok(serialized) = serde_json::to_string(&reconstructed_state) {
                        LAST_SAVED_STATE = Some(serialized.clone());
                        GAME_STATE = Some(reconstructed_state.clone());
                        let _ = save_state_to_oracle(&serialized);
                        eprintln!("[INFO] Reconstructed state saved to static variables and Oracle");
                    }
                }
            }
            
            let response = json!({
                "status": "success",
                "runtime": "wasmtime",
                "message": "TxEventQ state reconstruction completed",
                "reconstructed_players": reconstructed_state.len(),
                "players": reconstructed_state.keys().collect::<Vec<_>>(),
                "timestamp": get_timestamp()
            });

            response_builder
                .status_code(200)
                .header("Content-Type", "application/json")
                .body(response.to_string())
                .build()
        },
        // Standard game endpoints
        (Method::Get, "/") => handle_root(),
        (Method::Get, "/health") => handle_health(),
        (Method::Post, "/join") => handle_join(req),
        (Method::Post, "/move") => handle_move(req),
        (Method::Post, "/leave") => handle_leave(req),
        (Method::Get, "/players") => handle_players(),
        (Method::Get, "/leaderboard") => handle_leaderboard(),
        (Method::Post, "/test-kafka") => handle_test_kafka(req),
        (Method::Get, "/consume-kafka") => handle_consume_kafka(),
        (Method::Get, "/drain-messages") => handle_drain_messages(),
        (Method::Post, "/ai-action") => handle_ai_action(req),
        _ => Response::builder()
            .status_code(404)
            .header("Access-Control-Allow-Origin", "*")
            .body(r#"{"error": "Endpoint not found", "runtime": "wasmtime", "castle": "Temporal Sanctuary"}"#.to_string())
            .build(),
    }
}

fn handle_root() -> Result<Response, ErrorCode> {
    let debug_status = is_debug_enabled();
    
    let info = json!({
        "message": "WasiCycles Temporal Sanctuary is running!",
        "runtime": "wasmtime",
        "castle": "Temporal Sanctuary",
        "color": "#06b6d4",
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
            "ai-action": "/ai-action (POST)",
            "debug": {
                "enable": "/debug/enable",
                "disable": "/debug/disable", 
                "status": "/debug/status",
                "wasmtime-behavior": "/debug/wasmtime-behavior"
            }
        },
        "debug_logging": debug_status,
        "integration": {
            "kafka": "Oracle Database Kafka API",
            "scores": "Oracle ORDS",
            "https": "enabled via waki"
        },
        "timestamp": get_timestamp()
    });

    Response::builder()
        .header("Content-Type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(info.to_string())
        .build()
}

fn handle_health() -> Result<Response, ErrorCode> {
    let players = get_game_state();
    let health = json!({
        "status": "healthy",
        "runtime": "wasmtime", 
        "castle": "Wasmtime Cycle",
        "service": "WasiCycles Wasmtime Cycle",
        "version": "1.0.0",
        "color": "#06b6d4",
        "active_players": players.len(),
        "oracle_integration": "enabled",
        "https_support": "enabled via waki",
        "timestamp": get_timestamp()
    });

    Response::builder()
        .header("Content-Type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(health.to_string())
        .build()
}

fn handle_join(req: Request) -> Result<Response, ErrorCode> {
    eprintln!("[INFO] Join request received");
    
    let body = req.body().unwrap_or_default();
    let body_str = match String::from_utf8(body) {
        Ok(s) => {
            if is_debug_enabled() {
                eprintln!("[DEBUG] Request body: {}", s);
            }
            s
        },
        Err(e) => {
            eprintln!("[ERROR] Failed to parse request body as UTF-8: {:?}", e);
            return Err(ErrorCode::InternalError(None));
        }
    };
    
    // Extract player_id from request
    let player_id = match serde_json::from_str::<serde_json::Value>(&body_str) {
        Ok(json) => {
            if let Some(id) = json.get("player_id") {
                if id.is_string() {
                    id.as_str().unwrap().to_string()
                } else {
                    id.to_string().replace("\"", "")
                }
            } else {
                match extract_json_field(&body_str, "player_id") {
                    Some(id) => id,
                    None => {
                        eprintln!("[ERROR] Failed to extract player_id from request");
                        return Response::builder()
                            .status_code(400)
                            .header("Content-Type", "application/json")
                            .header("Access-Control-Allow-Origin", "*")
                            .body(r#"{"error": "Missing player_id in request", "status": "error"}"#.to_string())
                            .build();
                    }
                }
            }
        },
        Err(_) => {
            match extract_json_field(&body_str, "player_id") {
                Some(id) => id,
                None => {
                    eprintln!("[ERROR] Failed to extract player_id from request");
                    return Response::builder()
                        .status_code(400)
                        .header("Content-Type", "application/json")
                        .header("Access-Control-Allow-Origin", "*")
                        .body(r#"{"error": "Missing player_id in request", "status": "error"}"#.to_string())
                        .build();
                }
            }
        }
    };

    eprintln!("[INFO] Creating new player with ID: {}", player_id);
    
    // Get game state directly using our helper function
    let players = get_game_state();
    let in_memory = players.contains_key(&player_id);
    
    // Create new player
    let new_player = Player {
        id: player_id.clone(),
        x: 25.0, // Starting position - match other runtimes
        y: 25.0, // Starting position - match other runtimes
        direction: "up".to_string(),
        score: 0,
        color: "#06b6d4".to_string(), // Cyan for Wasmtime
        alive: true,
    };

    // Add player to in-memory state
    players.insert(player_id.clone(), new_player.clone());
    
    if in_memory {
        if is_debug_enabled() {
            eprintln!("[DEBUG] Player {} updated in memory", player_id);
        }
    } else {
        eprintln!("[INFO] Player {} added to memory", player_id);
    }
    
    // Save game state to ensure persistence across requests
    if let Err(e) = save_game_state(players) {
        eprintln!("[WARN] Failed to save game state: {:?}", e);
    } else if is_debug_enabled() {
        eprintln!("[DEBUG] Game state saved with player {}", player_id);
    }

    // Create join event
    let join_event = GameEvent {
        event_type: "player_joined".to_string(),
        player_id: player_id.clone(),
        game_id: "wasicycles-multiplayer".to_string(),
        runtime: "wasmtime".to_string(),
        castle: "Temporal Sanctuary".to_string(),
        timestamp: get_timestamp(),
        position: Some(Position { x: new_player.x, y: new_player.y }),
        direction: Some(new_player.direction.clone()),
        score: Some(new_player.score),
    };

    // Publish to Oracle Kafka
    match publish_to_oracle_kafka(&join_event) {
        Ok(_) => {
            if is_debug_enabled() {
                eprintln!("[DEBUG] Successfully published join event to Kafka");
            }
        },
        Err(e) => eprintln!("[WARN] Failed to publish to Kafka: {:?}", e),
    }
    
    // Try to save to ORDS as well (but don't fail if it doesn't work)
    if let Err(e) = save_player_to_ords(&new_player) {
        eprintln!("[WARN] Failed to save player to ORDS: {}", e);
    } else {
        eprintln!("[INFO] Player {} saved to ORDS successfully", player_id);
    }

    let response = json!({
        "status": "success",
        "runtime": "wasmtime",
        "castle": "Temporal Sanctuary", 
        "message": format!("Player {} joined Temporal Sanctuary", player_id),
        "player": {
            "id": new_player.id,
            "x": new_player.x,
            "y": new_player.y,
            "direction": new_player.direction,
            "score": new_player.score,
            "color": new_player.color,
            "alive": new_player.alive
        },
        "temporal_power": "activated",
        "oracle_integration": "active",
        "timestamp": get_timestamp()
    });

    eprintln!("[INFO] Sending join response for player {}", player_id);
    
    Response::builder()
        .header("Content-Type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(response.to_string())
        .build()
}

fn handle_move(req: Request) -> Result<Response, ErrorCode> {
    if is_debug_enabled() {
        eprintln!("[DEBUG] Move request received");
    } else {
        eprintln!("[INFO] Move request received");
    }
    
    let body = req.body().unwrap_or_default();
    let body_str = match String::from_utf8(body) {
        Ok(s) => {
            if is_debug_enabled() {
                eprintln!("[DEBUG] Move request body: {}", s);
            }
            s
        },
        Err(e) => {
            eprintln!("[ERROR] Failed to parse request body as UTF-8: {:?}", e);
            return Err(ErrorCode::InternalError(None));
        },
    };
    
    // Extract player_id from request
    let player_id = match serde_json::from_str::<serde_json::Value>(&body_str) {
        Ok(json) => {
            if let Some(id) = json.get("player_id") {
                if id.is_string() {
                    id.as_str().unwrap().to_string()
                } else {
                    id.to_string().replace("\"", "")
                }
            } else {
                match extract_json_field(&body_str, "player_id") {
                    Some(id) => id,
                    None => {
                        eprintln!("[ERROR] Failed to extract player_id from move request");
                        return Response::builder()
                            .status_code(400)
                            .header("Content-Type", "application/json")
                            .header("Access-Control-Allow-Origin", "*")
                            .body(r#"{"error": "Missing player_id in request", "status": "error"}"#.to_string())
                            .build();
                    }
                }
            }
        },
        Err(_) => {
            match extract_json_field(&body_str, "player_id") {
                Some(id) => id,
                None => {
                    eprintln!("[ERROR] Failed to extract player_id from move request");
                    return Response::builder()
                        .status_code(400)
                        .header("Content-Type", "application/json")
                        .header("Access-Control-Allow-Origin", "*")
                        .body(r#"{"error": "Missing player_id in request", "status": "error"}"#.to_string())
                        .build();
                }
            }
        }
    };
    
    // Extract direction from request
    let direction = match serde_json::from_str::<serde_json::Value>(&body_str) {
        Ok(json) => {
            if let Some(dir) = json.get("direction") {
                if dir.is_string() {
                    dir.as_str().unwrap().to_string()
                } else {
                    dir.to_string().replace("\"", "")
                }
            } else {
                match extract_json_field(&body_str, "direction") {
                    Some(dir) => dir,
                    None => {
                        eprintln!("[ERROR] Failed to extract direction from move request");
                        return Response::builder()
                            .status_code(400)
                            .header("Content-Type", "application/json")
                            .header("Access-Control-Allow-Origin", "*")
                            .body(r#"{"error": "Missing direction in request", "status": "error"}"#.to_string())
                            .build();
                    }
                }
            }
        },
        Err(_) => {
            match extract_json_field(&body_str, "direction") {
                Some(dir) => dir,
                None => {
                    eprintln!("[ERROR] Failed to extract direction from move request");
                    return Response::builder()
                        .status_code(400)
                        .header("Content-Type", "application/json")
                        .header("Access-Control-Allow-Origin", "*")
                        .body(r#"{"error": "Missing direction in request", "status": "error"}"#.to_string())
                        .build();
                }
            }
        }
    };
    
    // Use the game state with minimal logging
    let players = get_game_state();
    
    let default_player = Player {
        id: player_id.clone(),
        x: 25.0,
        y: 25.0,
        direction: "up".to_string(),
        score: 0,
        color: "#06b6d4".to_string(), // Cyan for Wasmtime
        alive: true,
    };
    
    // Get existing player or create a new one
    let player = if players.contains_key(&player_id) {
        if is_debug_enabled() {
            eprintln!("[DEBUG] Player {} found in state", player_id);
        }
        players.get(&player_id).unwrap().clone()
    } else {
        eprintln!("[INFO] Player {} not found, creating new player", player_id);
        // Create new player if not found
        let new_player = default_player.clone();
        players.insert(player_id.clone(), new_player.clone());
        // Save the state immediately to ensure persistence
        if let Err(e) = save_game_state(players) {
            eprintln!("[WARN] Failed to save game state for new player: {:?}", e);
        }
        new_player
    };
    
    // Store old position for logging
    let old_x = player.x;
    let old_y = player.y;
    
    // Create a mutable copy of the player to update
    let mut updated_player = player.clone();
    
    // Update direction
    updated_player.direction = direction.clone();
    
    // Move player based on direction
    match direction.as_str() {
        "up" => updated_player.y = (updated_player.y - 1.0).max(0.0),
        "down" => updated_player.y = (updated_player.y + 1.0).min(49.0),
        "left" => updated_player.x = (updated_player.x - 1.0).max(0.0),
        "right" => updated_player.x = (updated_player.x + 1.0).min(49.0),
        _ => {
            eprintln!("[ERROR] Invalid direction: {}", direction);
            return Response::builder()
                .status_code(400)
                .header("Content-Type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .body(json!({"error": "Invalid direction", "received": direction}).to_string())
                .build();
        }
    }
    
    // Log position change
    eprintln!("[INFO] Player {} moved {} from ({}, {}) to ({}, {})", 
        player_id, direction, old_x, old_y, updated_player.x, updated_player.y);
    
    // Increment score
    updated_player.score += 1;
    
    // Update player in memory
    players.insert(player_id.clone(), updated_player.clone());        
    
    // Save game state to ensure persistence across requests
    if let Err(e) = save_game_state(players) {
        eprintln!("[WARN] Failed to save game state after move: {:?}", e);
    } else if is_debug_enabled() {
        eprintln!("[DEBUG] Game state saved after player {} moved", player_id);
    }
    
    // Create move event
    let move_event = GameEvent {
        event_type: "player_moved".to_string(),
        player_id: player_id.clone(),
        game_id: "wasicycles-multiplayer".to_string(),
        runtime: "wasmtime".to_string(),
        castle: "Temporal Sanctuary".to_string(),
        timestamp: get_timestamp(),
        position: Some(Position { x: updated_player.x, y: updated_player.y }),
        direction: Some(updated_player.direction.clone()),
        score: Some(updated_player.score),
    };

    // Publish to Oracle Kafka
    // Publish to Oracle Kafka
    match publish_to_oracle_kafka(&move_event) {
        Ok(_) => {
            if is_debug_enabled() {
                eprintln!("[DEBUG] Successfully published move event to Kafka");
            }
        },
        Err(e) => eprintln!("[WARN] Failed to publish move event to Kafka: {:?}", e),
    }
    
    // Try to save updated player to ORDS as well (but don't fail if it doesn't work)
    if let Err(e) = save_player_to_ords(&updated_player) {
        eprintln!("[WARN] Failed to save updated player to ORDS: {}", e);
    } else if is_debug_enabled() {
        eprintln!("[DEBUG] Updated player {} saved to ORDS successfully", player_id);
    }

    let response = json!({
        "status": "success",
        "runtime": "wasmtime",
        "castle": "Temporal Sanctuary",
        "message": format!("Player {} moved {} in Temporal Sanctuary", player_id, direction),
        "player": {
            "id": updated_player.id,
            "x": updated_player.x,
            "y": updated_player.y,
            "direction": updated_player.direction,
            "score": updated_player.score,
            "color": updated_player.color,
            "alive": updated_player.alive
        },
        "temporal_power": "flowing",
        "oracle_integration": "active",
        "timestamp": get_timestamp()
    });

    Response::builder()
        .header("Content-Type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(response.to_string())
        .build()
}

fn handle_leave(req: Request) -> Result<Response, ErrorCode> {
    let body = req.body().unwrap_or_default();
    let body_str = match String::from_utf8(body) {
        Ok(s) => s,
        Err(_) => return Err(ErrorCode::InternalError(None)),
    };
    
    let player_id = match extract_json_field(&body_str, "player_id") {
        Some(id) => id,
        None => return Err(ErrorCode::InternalError(None)),
    };

    // Remove player from game state
    let players = get_game_state();
    let removed_player = players.remove(&player_id);

    if let Some(player) = removed_player {
        // Create leave event
        let leave_event = GameEvent {
            event_type: "player_left".to_string(),
            player_id: player_id.clone(),
            game_id: "wasicycles-multiplayer".to_string(),
            runtime: "wasmtime".to_string(),
            castle: "Temporal Sanctuary".to_string(),
            timestamp: get_timestamp(),
            position: None,
            direction: None,
            score: Some(player.score),
        };

        let _ = publish_to_oracle_kafka(&leave_event);
        
        // Save game state after player leaves
        let _ = save_game_state(players);
        if is_debug_enabled() {
            eprintln!("[DEBUG] Game state saved after player {} left", player_id);
        }

        let response = json!({
            "status": "success",
            "runtime": "wasmtime",
            "castle": "Temporal Sanctuary",
            "message": format!("Player {} left Temporal Sanctuary", player_id),
            "final_score": player.score,
            "temporal_status": "disconnected",
            "timestamp": get_timestamp()
        });

        Response::builder()
            .header("Content-Type", "application/json")
            .header("Access-Control-Allow-Origin", "*")
            .body(response.to_string())
            .build()
    } else {
        Response::builder()
            .status_code(404)
            .header("Access-Control-Allow-Origin", "*")
            .body(json!({"error": "Player not found"}).to_string())
            .build()
    }
}

fn handle_players() -> Result<Response, ErrorCode> {
    // Use the same state loading mechanism as other handlers
    let players = get_game_state();
    let players_vec: Vec<&Player> = players.values().collect();
    
    // Very minimal logging - only when debug is enabled
    if is_debug_enabled() && !players_vec.is_empty() {
        eprintln!("[DEBUG] Players endpoint returning {} players", players_vec.len());
    }
    
    // Only log details when players exist
    if !players_vec.is_empty() {
        let player_ids: Vec<&String> = players_vec.iter().map(|p| &p.id).collect();
        eprintln!("[INFO] Player IDs in state: {:?}", player_ids);
        
        if is_debug_enabled() {
            for player in &players_vec {
                eprintln!("[DEBUG] Player {}: x={}, y={}, score={}", player.id, player.x, player.y, player.score);
            }
        }
    }

    let response = json!({
        "runtime": "wasmtime",
        "castle": "Temporal Sanctuary",
        "players": players_vec.iter().map(|p| {
            json!({
                "id": p.id,
                "x": p.x,
                "y": p.y,
                "score": p.score,
                "color": p.color,
                "direction": p.direction,
                "alive": p.alive
            })
        }).collect::<Vec<_>>(),
        "count": players_vec.len(),
        "timestamp": get_timestamp()
    });

    Response::builder()
        .header("Content-Type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(response.to_string())
        .build()
}

fn handle_leaderboard() -> Result<Response, ErrorCode> {
    match get_leaderboard_ords() {
        Ok(leaderboard_str) => {
            // Parse the leaderboard JSON string
            let leaderboard: Value = serde_json::from_str(&leaderboard_str).unwrap_or(json!({"items": []}));
            
            let response = json!({
                "runtime": "wasmtime",
                "castle": "Temporal Sanctuary",
                "leaderboard": leaderboard,
                "source": "Oracle ORDS",
                "timestamp": get_timestamp()
            });

            Response::builder()
                .header("Content-Type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .body(response.to_string())
                .build()
        }
        Err(_) => {
            Response::builder()
                .status_code(500)
                .header("Access-Control-Allow-Origin", "*")
                .body(json!({"error": "Failed to get leaderboard"}).to_string())
                .build()
        }
    }
}

fn handle_test_kafka(req: Request) -> Result<Response, ErrorCode> {
    let body = req.body().unwrap_or_default();
    let body_str = match String::from_utf8(body) {
        Ok(s) => s,
        Err(_) => "{}".to_string(),
    };
    
    // Parse test message from request body
    let test_message = extract_json_field(&body_str, "test_message")
        .unwrap_or_else(|| "default_test_from_wasmtime".to_string());
    
    let test_event = GameEvent {
        event_type: "connectivity_test".to_string(),
        player_id: format!("test-wasmtime-{}", test_message),
        game_id: "wasicycles-multiplayer".to_string(),
        runtime: "wasmtime".to_string(),
        castle: "Temporal Sanctuary".to_string(),
        timestamp: get_timestamp(),
        position: Some(Position { x: 0.0, y: 0.0 }),
        direction: Some("test".to_string()),
        score: Some(42),
    };

    match publish_to_oracle_kafka(&test_event) {
        Ok(_) => {
            let response = json!({
                "status": "success",
                "runtime": "wasmtime",
                "castle": "Temporal Sanctuary",
                "message": "Oracle TxEventQ connectivity test successful",
                "test_event": test_event,
                "kafka_topic": get_kafka_topic(),
                "oracle_url": get_oracle_base_url(),
                "timestamp": get_timestamp()
            });

            Response::builder()
                .header("Content-Type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .body(response.to_string())
                .build()
        }
        Err(error) => {
            let response = json!({
                "status": "error",
                "runtime": "wasmtime",
                "castle": "Temporal Sanctuary",
                "message": "Oracle TxEventQ connectivity test failed",
                "error": error,
                "kafka_topic": get_kafka_topic(),
                "oracle_url": get_oracle_base_url(),
                "timestamp": get_timestamp()
            });

            Response::builder()
                .status_code(500)
                .header("Content-Type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .body(response.to_string())
                .build()
        }
    }
}

fn handle_consume_kafka() -> Result<Response, ErrorCode> {
    // Consume messages from Oracle TxEventQ using consumer group pattern
    let messages = consume_from_oracle_kafka();
    
    let response_data = json!({
        "status": "success", 
        "runtime": "wasmtime",
        "castle": "Temporal Sanctuary",
        "endpoint": "consume_kafka",
        "messages": messages,
        "count": messages.len(),
        "timestamp": get_timestamp()
    });

    Response::builder()
        .header("Content-Type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(response_data.to_string())
        .build()
}

fn handle_drain_messages() -> Result<Response, ErrorCode> {
    // Drain all messages from Oracle TxEventQ for this runtime
    let mut all_messages = Vec::new();
    let mut batch_count = 0;
    let max_batches = 10;  // Prevent infinite loop

    // Keep consuming until no more messages or max batches reached
    loop {
        if batch_count >= max_batches {
            break;
        }
        
        let messages = consume_from_oracle_kafka();
        
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
        std::thread::sleep(std::time::Duration::from_millis(100));
    }

    let response_data = json!({
        "status": "success",
        "runtime": "wasmtime",
        "castle": "Temporal Sanctuary", 
        "endpoint": "drain_messages",
        "messages_drained": all_messages,
        "total_count": all_messages.len(),
        "batches_processed": batch_count,
        "timestamp": get_timestamp()
    });

    Response::builder()
        .header("Content-Type", "application/json")
        .header("Access-Control-Allow-Origin", "*")
        .body(response_data.to_string())
        .build()
}

fn consume_from_oracle_kafka() -> Vec<Value> {
    // Use the correct Oracle TxEventQ pattern as per reference documentation
    let client = Client::new();
    let topic_name = get_kafka_topic();
    let topic_safe = topic_name.to_lowercase().replace("_", "");
    let consumer_group_id = format!("wasmtime_{}_grp", topic_safe);
    let auth = base64_encode(&format!("{}:{}", get_oracle_user(), get_oracle_password()));
    
    eprintln!("[DEBUG] Topic: {}, Consumer Group: {}", topic_name, consumer_group_id);
    
    // Base URL for TxEventQ API (consistent across all calls)
    let txeventq_base_url = get_txeventq_base_url();
    let cluster_name = get_oracle_db_name();
    
    // Step 1: Create consumer group using correct URL pattern
    let create_group_url = format!("{}/clusters/{}/consumer-groups/{}", 
        txeventq_base_url, cluster_name, consumer_group_id);
    
    let group_payload = json!({
        "topic_name": topic_name
    });
    
    eprintln!("[DEBUG] Consumer group URL: {}", create_group_url);
    eprintln!("[DEBUG] Consumer group payload: {}", group_payload);
    
    let _group_result = client
        .post(&create_group_url)
        .headers([
            ("Content-Type", "application/json"),
            ("Authorization", &format!("Basic {}", auth))
        ])
        .body(group_payload.to_string().as_bytes().to_vec())
        .send();
    
    // Step 2: Create consumer instance using correct pattern (POST /consumers/{group})
    let consumer_instance_url = format!("{}/consumers/{}", 
        txeventq_base_url, consumer_group_id);
    let consumer_payload = json!({});  // Empty payload for consumer creation
    
    let consumer_instance_result = client
        .post(&consumer_instance_url)
        .headers([
            ("Content-Type", "application/json"),
            ("Authorization", &format!("Basic {}", auth))
        ])
        .body(consumer_payload.to_string().as_bytes().to_vec())
        .send();
    
    // Extract consumer instance ID from response
    let consumer_instance_id = if let Ok(resp) = consumer_instance_result {
        if resp.status_code() >= 200 && resp.status_code() < 300 {
            if let Ok(body) = resp.body() {
                if let Ok(body_str) = String::from_utf8(body.clone()) {
                    eprintln!("[DEBUG] Consumer instance response: {}", body_str);
                    if let Ok(consumer_data) = serde_json::from_str::<Value>(&body_str) {
                        let instance_id = consumer_data.get("instance_id")
                            .and_then(|v| v.as_str())
                            .unwrap_or("unknown_instance")
                            .to_string();
                        eprintln!("[INFO] Created consumer instance: {}", instance_id);
                        instance_id
                    } else {
                        eprintln!("[WARN] Could not parse consumer instance response as JSON");
                        "unknown_instance".to_string()
                    }
                } else {
                    eprintln!("[WARN] Could not decode consumer instance response");
                    "unknown_instance".to_string()
                }
            } else {
                eprintln!("[WARN] Could not read consumer instance response body");
                "unknown_instance".to_string()
            }
        } else {
            eprintln!("[WARN] Consumer instance creation failed: status {}", resp.status_code());
            "unknown_instance".to_string()
        }
    } else {
        eprintln!("[WARN] Consumer instance request failed");
        "unknown_instance".to_string()
    };
    
    // Step 3: Consume records using correct pattern: /consumers/{group}/instances/{instance_id}/records
    let consume_url = format!("{}/consumers/{}/instances/{}/records", 
        txeventq_base_url, consumer_group_id, consumer_instance_id);
    
    eprintln!("[DEBUG] Consuming from URL: {}", consume_url);
    eprintln!("[DEBUG] Consumer group: {}", consumer_group_id);
    eprintln!("[DEBUG] Consumer instance: {}", consumer_instance_id);
    eprintln!("[DEBUG] Topic: {}", topic_name);
    
    let consume_result = client
        .get(&consume_url)
        .headers([
            ("Accept", "application/json"),
            ("Authorization", &format!("Basic {}", auth))
        ])
        .send();
    
    if let Ok(resp) = consume_result {
        if resp.status_code() >= 200 && resp.status_code() < 300 {
            if let Ok(body) = resp.body() {
                if let Ok(body_str) = String::from_utf8(body.clone()) {
                    eprintln!("[DEBUG] TxEventQ consume response: {}", body_str);
                    
                    if body_str.trim() == "[]" {
                        eprintln!("[DEBUG] Empty response - no messages available or consumer offset is at end");
                    }
                    
                    if !body_str.trim().is_empty() && body_str != "[]" {
                        // Try to parse the response as an array of records
                        if let Ok(records) = serde_json::from_slice::<Vec<Value>>(&body) {
                            let mut processed_messages = Vec::new();
                            
                            for record in records {
                                // Process each record using correct pattern
                                let value = record.get("value").cloned().unwrap_or(Value::Null);
                                let parsed_value = if let Value::String(s) = &value {
                                    // Try to parse the value string as JSON
                                    serde_json::from_str(s).unwrap_or(value.clone())
                                } else {
                                    value.clone()
                                };
                                
                                let processed_msg = json!({
                                    "topic": record.get("topic").cloned().unwrap_or(json!(get_kafka_topic())),
                                    "partition": record.get("partition").cloned().unwrap_or(json!(0)),
                                    "offset": record.get("offset").cloned().unwrap_or(json!("unknown")),
                                    "timestamp": record.get("timestamp").cloned().unwrap_or(json!(get_timestamp())),
                                    "key": record.get("key").cloned(),
                                    "data": parsed_value,
                                    "consumed_by": "wasmtime",
                                    "consumed_at": get_timestamp(),
                                    "instance_id": consumer_instance_id.clone()  // Include instance ID for debugging
                                });
                                processed_messages.push(processed_msg);
                            }
                            
                            eprintln!("[INFO] Processed {} messages with instance_id: {}", processed_messages.len(), consumer_instance_id);
                            return processed_messages;
                        }
                    }
                }
            }
        } else {
            eprintln!("[WARN] TxEventQ consume failed: status {}", resp.status_code());
        }
    } else {
        eprintln!("[WARN] TxEventQ consume request failed");
    }
    
    // If we reached here, either there were no messages or an error occurred
    vec![json!({
        "message": "Either no messages available or error in consumption",
        "consumed_by": "wasmtime",
        "consumed_at": get_timestamp(),
        "instance_id": consumer_instance_id,
        "note": "Updated to use correct instance_id pattern"
    })]
}

// TxEventQ Event Sourcing for State Reconstruction
// This addresses Wasmtime's static variable reset between requests
fn reconstruct_state_from_events() -> HashMap<String, Player> {
    unsafe {
        let current_time = get_timestamp();
        
        // Rate limit: only allow reconstruction once every 10 seconds to prevent excessive calls
        if current_time - LAST_RECONSTRUCTION_TIME < 10000 {
            eprintln!("[INFO] Skipping state reconstruction due to rate limit (last: {}ms ago)", current_time - LAST_RECONSTRUCTION_TIME);
            return HashMap::new();
        }
        
        LAST_RECONSTRUCTION_TIME = current_time;
    }
    
    let mut reconstructed_state = HashMap::new();
    
    eprintln!("[INFO] Reconstructing game state from TxEventQ events due to Wasmtime static reset");
    
    // Consume recent events from TxEventQ to rebuild state
    let messages = consume_recent_game_events();
    
    for message in messages {
        if let Some(event_data) = message.get("data") {
            if let Ok(event) = serde_json::from_value::<GameEvent>(event_data.clone()) {
                match event.event_type.as_str() {
                    "player_snapshot" => {
                        // State snapshots have the most recent player state
                        let player = Player {
                            id: event.player_id.clone(),
                            x: event.position.as_ref().map(|p| p.x).unwrap_or(25.0),
                            y: event.position.as_ref().map(|p| p.y).unwrap_or(25.0),
                            direction: event.direction.unwrap_or_else(|| "up".to_string()),
                            score: event.score.unwrap_or(0),
                            color: "#06b6d4".to_string(), // Wasmtime color
                            alive: true,
                        };
                        reconstructed_state.insert(event.player_id.clone(), player);
                        eprintln!("[DEBUG] Reconstructed player {} from snapshot", event.player_id);
                    },
                    "player_joined" => {
                        // Only use join events if we don't have a snapshot
                        if !reconstructed_state.contains_key(&event.player_id) {
                            let player = Player {
                                id: event.player_id.clone(),
                                x: event.position.as_ref().map(|p| p.x).unwrap_or(25.0),
                                y: event.position.as_ref().map(|p| p.y).unwrap_or(25.0),
                                direction: event.direction.unwrap_or_else(|| "up".to_string()),
                                score: event.score.unwrap_or(0),
                                color: "#06b6d4".to_string(), // Wasmtime color
                                alive: true,
                            };
                            reconstructed_state.insert(event.player_id.clone(), player);
                            eprintln!("[DEBUG] Reconstructed player {} from join event", event.player_id);
                        }
                    },
                    "player_moved" => {
                        if let Some(player) = reconstructed_state.get_mut(&event.player_id) {
                            if let Some(pos) = &event.position {
                                player.x = pos.x;
                                player.y = pos.y;
                            }
                            if let Some(dir) = &event.direction {
                                player.direction = dir.clone();
                            }
                            if let Some(score) = event.score {
                                player.score = score;
                            }
                            eprintln!("[DEBUG] Updated player {} from move event", event.player_id);
                        } else {
                            // Player not found, create from move event
                            let player = Player {
                                id: event.player_id.clone(),
                                x: event.position.as_ref().map(|p| p.x).unwrap_or(25.0),
                                y: event.position.as_ref().map(|p| p.y).unwrap_or(25.0),
                                direction: event.direction.unwrap_or_else(|| "up".to_string()),
                                score: event.score.unwrap_or(0),
                                color: "#06b6d4".to_string(),
                                alive: true,
                            };
                            reconstructed_state.insert(event.player_id.clone(), player);
                            eprintln!("[DEBUG] Created player {} from move event", event.player_id);
                        }
                    },
                    "player_left" => {
                        reconstructed_state.remove(&event.player_id);
                        eprintln!("[DEBUG] Removed player {} from leave event", event.player_id);
                    },
                    _ => {
                        // Ignore other event types for now
                    }
                }
            }
        }
    }
    
    let player_count = reconstructed_state.len();
    if player_count > 0 {
        eprintln!("[INFO] Reconstructed state with {} players from TxEventQ events", player_count);
        let player_ids: Vec<&String> = reconstructed_state.keys().collect();
        eprintln!("[INFO] Reconstructed players: {:?}", player_ids);
    } else {
        eprintln!("[INFO] No players found in TxEventQ event history");
    }
    
    reconstructed_state
}

// Consume recent game events for state reconstruction
fn consume_recent_game_events() -> Vec<Value> {
    let client = Client::new();
    let consumer_group_id = "wasmtime_state_reconstruction";
    let consumer_id = "wasmtime_consumer_state";
    let auth = base64_encode(&format!("{}:{}", get_oracle_user(), get_oracle_password()));
    
    let mut messages = Vec::new();
    
    // Try to consume from a dedicated topic for state reconstruction if available
    // Otherwise use the main game events topic
    let topics_to_try = vec![
        format!("{}_STATE", get_kafka_topic()), // Try dedicated state topic first
        get_kafka_topic(),                       // Fall back to main topic
    ];
    
    for topic in topics_to_try {
        // Create consumer group for this topic
        let create_group_url = format!("{}/clusters/{}/consumer-groups/{}", 
            get_txeventq_base_url(), get_oracle_db_name(), consumer_group_id);
        
        let group_payload = json!({
            "topic_name": topic
        });
        
        let _group_result = client
            .post(&create_group_url)
            .headers([
                ("Content-Type", "application/json"),
                ("Authorization", &format!("Basic {}", auth))
            ])
            .body(group_payload.to_string().as_bytes().to_vec())
            .send();
        
        // Create consumer
        let create_consumer_url = format!("{}/clusters/{}/consumer-groups/{}/consumers/{}", 
            get_txeventq_base_url(), get_oracle_db_name(), consumer_group_id, consumer_id);
        
        let _consumer_result = client
            .post(&create_consumer_url)
            .headers([
                ("Content-Type", "application/json"),
                ("Authorization", &format!("Basic {}", auth))
            ])
            .body("{}".as_bytes().to_vec())
            .send();
        
        // Subscribe to topic
        let subscribe_url = format!("{}/clusters/{}/consumer-groups/{}/consumers/{}/subscription", 
            get_txeventq_base_url(), get_oracle_db_name(), consumer_group_id, consumer_id);
        
        let subscription_payload = json!({
            "topic_name": topic
        });
        
        let _subscribe_result = client
            .post(&subscribe_url)
            .headers([
                ("Content-Type", "application/json"),
                ("Authorization", &format!("Basic {}", auth))
            ])
            .body(subscription_payload.to_string().as_bytes().to_vec())
            .send();
        
        // Consume records with a limit to get recent events
        let consume_url = format!("{}/clusters/{}/consumer-groups/{}/consumers/{}/records?limit=100", 
            get_txeventq_base_url(), get_oracle_db_name(), consumer_group_id, consumer_id);
        
        if let Ok(resp) = client
            .get(&consume_url)
            .headers([
                ("Accept", "application/json"),
                ("Authorization", &format!("Basic {}", auth))
            ])
            .send() 
        {
            if resp.status_code() >= 200 && resp.status_code() < 300 {
                if let Ok(body) = resp.body() {
                    if let Ok(body_str) = String::from_utf8(body.clone()) {
                        if !body_str.is_empty() && body_str != "[]" {
                            if let Ok(records) = serde_json::from_slice::<Vec<Value>>(&body) {
                                for record in records {
                                    let value = record.get("value").cloned().unwrap_or(Value::Null);
                                    let value_str = if let Value::String(s) = &value {
                                        s.clone()
                                    } else {
                                        serde_json::to_string(&value).unwrap_or_default()
                                    };
                                    
                                    let parsed_value = serde_json::from_str(&value_str).unwrap_or(value.clone());
                                    
                                    messages.push(json!({
                                        "topic": record.get("topic").cloned().unwrap_or(Value::Null),
                                        "data": parsed_value,
                                        "reconstructed_from": topic
                                    }));
                                }
                                
                                if !messages.is_empty() {
                                    eprintln!("[INFO] Found {} events in topic {} for state reconstruction", messages.len(), topic);
                                    break; // Found events, no need to try other topics
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Sort messages by timestamp if available to replay in order
    messages.sort_by(|a, b| {
        let ts_a = a.get("data")
            .and_then(|d| d.get("timestamp"))
            .and_then(|t| t.as_u64())
            .unwrap_or(0);
        let ts_b = b.get("data")
            .and_then(|d| d.get("timestamp"))
            .and_then(|t| t.as_u64())
            .unwrap_or(0);
        ts_a.cmp(&ts_b)
    });
    
    messages
}

// Oracle integration functions
fn get_created_topics() -> &'static mut std::collections::HashSet<String> {
    unsafe {
        if CREATED_TOPICS.is_none() {
            CREATED_TOPICS = Some(std::collections::HashSet::new());
        }
        CREATED_TOPICS.as_mut().unwrap()
    }
}

fn create_txeventq_topic(topic_name: &str) -> Result<(), String> {
    // Check cache first
    let created_topics = get_created_topics();
    if created_topics.contains(topic_name) {
        return Ok(());
    }

    let client = Client::new();
    let topic_config = json!({
        "topic_name": topic_name,
        "partitions_count": "1"
    });
    
    let auth = base64_encode(&format!("{}:{}", get_oracle_user(), get_oracle_password()));
    let url = format!("{}/clusters/{}/topics", get_txeventq_base_url(), get_oracle_db_name());

    let response = client
        .post(&url)
        .headers([
            ("Content-Type", "application/json"),
            ("Authorization", &format!("Basic {}", auth))
        ])
        .body(topic_config.to_string().as_bytes().to_vec())
        .send()
        .map_err(|e| format!("Topic creation request failed: {}", e))?;

    let status_code = response.status_code();
    
    match status_code {
        200..=299 => {
            // Success - add to cache
            created_topics.insert(topic_name.to_string());
            Ok(())
        }
        409 => {
            // Topic already exists - add to cache to avoid future attempts
            created_topics.insert(topic_name.to_string());
            Ok(())
        }
        _ => {
            let error_body = response.body().unwrap_or_default();
            let error_msg = String::from_utf8(error_body).unwrap_or_default();
            
            // If error message indicates topic exists, treat as success
            if error_msg.contains("already exists") || error_msg.contains("ALREADY_EXISTS") {
                created_topics.insert(topic_name.to_string());
                Ok(())
            } else {
                Err(format!("Topic creation failed with status {}: {}", status_code, error_msg))
            }
        }
    }
}

fn publish_to_oracle_kafka(event: &GameEvent) -> Result<(), String> {
    let client = Client::new();
    
    // Oracle TxEventQ REST API format (key difference: value must be JSON string, not object)
    let kafka_payload = json!({
        "records": [{
            "key": format!("wasmtime-{}", get_timestamp()),
            "value": serde_json::to_string(event).map_err(|e| format!("JSON serialization failed: {}", e))?  // JSON string, not object!
        }]
    });
    
    let auth = base64_encode(&format!("{}:{}", get_oracle_user(), get_oracle_password()));
    
    // Use the same base URL pattern as wasmedge
    let url = format!("{}/topics/{}", get_txeventq_base_url(), get_kafka_topic());

    let response = client
        .post(&url)
        .headers([
            ("Content-Type", "application/json"),
            ("Accept", "application/json"),
            ("Authorization", &format!("Basic {}", auth))
        ])
        .body(kafka_payload.to_string().as_bytes().to_vec())
        .send()
        .map_err(|e| format!("Kafka request failed: {}", e))?;

    let status_code = response.status_code();
    
    if status_code >= 200 && status_code < 300 {
        // Log success (similar to wasmedge pattern)
        eprintln!("✅ TxEventQ message published from Wasmtime: {} for player {}", 
                event.event_type, event.player_id);
        Ok(())
    } else {
        // Get error details
        let error_body = response.body().unwrap_or_default();
        let error_text = String::from_utf8(error_body).unwrap_or_default();
        eprintln!("❌ Oracle TxEventQ error from Wasmtime: {} - {}", status_code, error_text);
        Err(format!("Kafka error: {} - {}", status_code, error_text))
    }
}

fn update_player_score_ords(player_id: &str, score: i32, game_id: &str) -> Result<(), String> {
    let client = Client::new();
    let score_data = json!({
        "player_id": player_id,
        "score": score,
        "game_id": game_id,
        "runtime": "wasmtime",
        "castle": "Temporal Sanctuary",
        "timestamp": get_timestamp()
    });

    let auth = base64_encode(&format!("{}:{}", get_oracle_user(), get_oracle_password()));
    let url = format!("{}/scores/", get_ords_url());

    let response = client
        .post(&url)
        .headers([
            ("Content-Type", "application/json"),
            ("Authorization", &format!("Basic {}", auth))
        ])
        .body(score_data.to_string().as_bytes().to_vec())
        .send()
        .map_err(|e| format!("ORDS request failed: {}", e))?;

    let status_code = response.status_code();
    
    if status_code >= 200 && status_code < 300 {
        Ok(())
    } else {
        Err(format!("ORDS error: {}", status_code))
    }
}

fn get_leaderboard_ords() -> Result<String, String> {
    let client = Client::new();
    let auth = base64_encode(&format!("{}:{}", get_oracle_user(), get_oracle_password()));
    let url = format!("{}/leaderboard/", get_ords_url());

    let response = client
        .get(&url)
        .headers([
            ("Accept", "application/json"),
            ("Authorization", &format!("Basic {}", auth))
        ])
        .send()
        .map_err(|e| format!("Request failed: {}", e))?;

    let status_code = response.status_code();
    
    if status_code >= 200 && status_code < 300 {
        let body = response.body().map_err(|e| format!("Failed to read body: {}", e))?;
        String::from_utf8(body).map_err(|e| format!("Invalid UTF-8: {}", e))
    } else {
        Err(format!("ORDS leaderboard error: {}", status_code))
    }
}

// Function to save player data to Oracle ORDS
fn save_player_to_ords(player: &Player) -> Result<(), String> {
    let client = Client::new();
    let player_data = match serde_json::to_value(player) {
        Ok(data) => data,
        Err(e) => {
            eprintln!("[WARN] JSON serialization failed: {} - proceeding with in-memory only", e);
            return Ok(()); // Return success since we can continue with in-memory state
        }
    };
    
    let auth = base64_encode(&format!("{}:{}", get_oracle_user(), get_oracle_password()));
    let url = format!("{}/players/", get_ords_url());

    eprintln!("[INFO] Saving player {} to ORDS", player.id);
    
    // Get the status code first before consuming the response
    let response = match client
        .post(&url)
        .headers([
            ("Content-Type", "application/json"),
            ("Authorization", &format!("Basic {}", auth))
        ])
        .body(player_data.to_string().as_bytes().to_vec())
        .send() {
            Ok(resp) => resp,
            Err(e) => {
                eprintln!("[WARN] ORDS player save request failed: {} - continuing with in-memory state", e);
                return Ok(()); // Return success since we can continue with in-memory state
            }
        };

    let status_code = response.status_code();
    
    if status_code >= 200 && status_code < 300 {
        eprintln!("[INFO] Successfully saved player {} to ORDS", player.id);
        Ok(())
    } else {
        // Handle various HTTP error codes gracefully but don't fail
        let error_message = match status_code {
            404 => format!("ORDS player endpoint not found (404). Continuing with in-memory state."),
            405 => format!("ORDS doesn't allow POST to player endpoint (405). Continuing with in-memory state."),
            _ => format!("ORDS player save error: {}. Continuing with in-memory state.", status_code)
        };
        
        if status_code == 405 || status_code == 404 {
            eprintln!("[INFO] {}", error_message);
        } else {
            eprintln!("[WARN] {}", error_message);
        }
        
        // Return success even if ORDS fails since we can continue with in-memory state
        Ok(())
    }
}

// Function to fetch player data from Oracle ORDS
fn fetch_player_from_ords(player_id: &str) -> Result<Player, String> {
    let client = Client::new();
    let auth = base64_encode(&format!("{}:{}", get_oracle_user(), get_oracle_password()));
    let url = format!("{}/players/{}", get_ords_url(), player_id);

    eprintln!("[INFO] Fetching player {} from ORDS", player_id);
    
    // Get the status code first before consuming the response
    let response = match client
        .get(&url)
        .headers([
            ("Accept", "application/json"),
            ("Authorization", &format!("Basic {}", auth))
        ])
        .send() {
            Ok(resp) => resp,
            Err(e) => {
                eprintln!("[WARN] ORDS player fetch request failed: {} - returning dummy player", e);
                // Return dummy player instead of error
                return Ok(Player {
                    id: player_id.to_string(),
                    x: 25.0,
                    y: 25.0,
                    direction: "up".to_string(),
                    score: 0,
                    color: "#06b6d4".to_string(), // Cyan for Wasmtime
                    alive: true,
                });
            }
        };

    let status_code = response.status_code();
    
    if status_code >= 200 && status_code < 300 {
        let body = match response.body() {
            Ok(b) => b,
            Err(e) => {
                eprintln!("[WARN] Failed to read ORDS response body: {} - returning dummy player", e);
                // Return dummy player
                return Ok(Player {
                    id: player_id.to_string(),
                    x: 25.0,
                    y: 25.0,
                    direction: "up".to_string(),
                    score: 0,
                    color: "#06b6d4".to_string(),
                    alive: true,
                });
            }
        };
        
        let body_str = match String::from_utf8(body) {
            Ok(s) => s,
            Err(e) => {
                eprintln!("[WARN] Failed to parse ORDS response as UTF-8: {} - returning dummy player", e);
                // Return dummy player
                return Ok(Player {
                    id: player_id.to_string(),
                    x: 25.0,
                    y: 25.0,
                    direction: "up".to_string(),
                    score: 0,
                    color: "#06b6d4".to_string(),
                    alive: true,
                });
            }
        };
        
        match serde_json::from_str::<Player>(&body_str) {
            Ok(player) => {
                eprintln!("[INFO] Successfully fetched player {} from ORDS", player_id);
                return Ok(player);
            },
            Err(e) => {
                eprintln!("[WARN] Failed to parse ORDS player data: {} - returning dummy player", e);
                // Return dummy player
                return Ok(Player {
                    id: player_id.to_string(),
                    x: 25.0,
                    y: 25.0,
                    direction: "up".to_string(),
                    score: 0,
                    color: "#06b6d4".to_string(),
                    alive: true,
                });
            }
        }
    } else if status_code == 404 {
        // 404 means player not found - return dummy player
        eprintln!("[INFO] Player {} not found in ORDS (404) - returning dummy player", player_id);
        return Ok(Player {
            id: player_id.to_string(),
            x: 25.0,
            y: 25.0,
            direction: "up".to_string(),
            score: 0,
            color: "#06b6d4".to_string(),
            alive: true,
        });
    } else if status_code == 405 {
        // 405 means method not allowed - return dummy player
        eprintln!("[INFO] ORDS doesn't allow GET for player endpoint (405) - returning dummy player");
        return Ok(Player {
            id: player_id.to_string(),
            x: 25.0,
            y: 25.0,
            direction: "up".to_string(),
            score: 0,
            color: "#06b6d4".to_string(),
            alive: true,
        });
    } else {
        // Other error codes - return dummy player
        eprintln!("[WARN] ORDS player fetch error: {} - returning dummy player", status_code);
        return Ok(Player {
            id: player_id.to_string(),
            x: 25.0,
            y: 25.0,
            direction: "up".to_string(),
            score: 0,
            color: "#06b6d4".to_string(),
            alive: true,
        });
    }
}

// Function to fetch all players from Oracle ORDS
fn fetch_all_players_from_ords() -> Result<HashMap<String, Player>, String> {
    let client = Client::new();
    let auth = base64_encode(&format!("{}:{}", get_oracle_user(), get_oracle_password()));
    let url = format!("{}/players/", get_ords_url());

    eprintln!("[INFO] Fetching all players from ORDS");
    
    // Get the status code first before consuming the response
    let response = match client
        .get(&url)
        .headers([
            ("Accept", "application/json"),
            ("Authorization", &format!("Basic {}", auth))
        ])
        .send() {
            Ok(resp) => resp,
            Err(e) => {
                eprintln!("[WARN] ORDS all players fetch request failed: {} - returning empty state", e);
                return Ok(HashMap::new()); // Return dummy empty state instead of error
            }
        };

    let status_code = response.status_code();
    
    if status_code >= 200 && status_code < 300 {
        let body = match response.body() {
            Ok(b) => b,
            Err(e) => {
                eprintln!("[WARN] Failed to read ORDS response body: {} - returning empty state", e);
                return Ok(HashMap::new()); // Return dummy empty state
            }
        };
        
        let body_str = match String::from_utf8(body) {
            Ok(s) => s,
            Err(e) => {
                eprintln!("[WARN] Failed to parse ORDS response as UTF-8: {} - returning empty state", e);
                return Ok(HashMap::new()); // Return dummy empty state
            }
        };
        
        // ORDS typically returns items in a "items" array
        match serde_json::from_str::<serde_json::Value>(&body_str) {
            Ok(json_value) => {
                if let Some(items) = json_value.get("items") {
                    if let Some(items_array) = items.as_array() {
                        let mut players_map = HashMap::new();
                        
                        for item in items_array {
                            if let Ok(player) = serde_json::from_value::<Player>(item.clone()) {
                                players_map.insert(player.id.clone(), player);
                            }
                        }
                        
                        eprintln!("[INFO] Successfully loaded {} players from ORDS", players_map.len());
                        return Ok(players_map);
                    }
                }
                
                // If we got here, the JSON was valid but didn't have the expected structure
                eprintln!("[INFO] ORDS returned valid JSON but no players were found");
                return Ok(HashMap::new()); // Return empty map instead of error
            },
            Err(e) => {
                eprintln!("[WARN] Failed to parse ORDS response JSON: {} - returning empty state", e);
                return Ok(HashMap::new()); // Return dummy empty state
            }
        }
    } else if status_code == 404 {
        // For 404, return empty map - this is not an error condition
        eprintln!("[INFO] ORDS endpoint returned 404 - assuming no players exist yet");
        return Ok(HashMap::new());
    } else if status_code == 405 {
        // For 405 Method Not Allowed, the endpoint doesn't support the HTTP method
        eprintln!("[INFO] ORDS endpoint returned 405 (Method Not Allowed) - proceeding with in-memory state");
        return Ok(HashMap::new());
    } else {
        // For other error codes, log but return empty map to continue with in-memory state
        eprintln!("[WARN] ORDS all players fetch error: {} - proceeding with in-memory state", status_code);
        return Ok(HashMap::new());
    }
}

// Publish state snapshots to TxEventQ for efficient reconstruction
fn publish_state_snapshot_to_kafka(players: &HashMap<String, Player>) -> Result<(), String> {
    if players.is_empty() {
        return Ok(()); // Don't publish empty snapshots
    }
    
    let state_snapshot = GameEvent {
        event_type: "state_snapshot".to_string(),
        player_id: "wasmtime_server".to_string(),
        game_id: "wasicycles-multiplayer".to_string(),
        runtime: "wasmtime".to_string(),
        castle: "Temporal Sanctuary".to_string(),
        timestamp: get_timestamp(),
        position: None,
        direction: None,
        score: None,
    };
    
    // For now, we'll use the existing publish mechanism
    // In the future, we could enhance this to include the full state
    publish_to_oracle_kafka(&state_snapshot)
}

// AI handling functions
fn handle_ai_action(req: Request) -> Result<Response, ErrorCode> {
    let body_str = match req.body() {
        Ok(body_bytes) => {
            match String::from_utf8(body_bytes) {
                Ok(s) => s,
                Err(e) => {
                    eprintln!("[ERROR] Failed to parse request body as UTF-8: {:?}", e);
                    return Response::builder()
                        .status_code(400)
                        .header("Access-Control-Allow-Origin", "*")
                        .header("Content-Type", "application/json")
                        .body(r#"{"error": "Invalid UTF-8", "runtime": "wasmtime"}"#.to_string())
                        .build();
                }
            }
        },
        Err(e) => {
            eprintln!("[ERROR] Failed to read request body: {:?}", e);
            return Response::builder()
                .status_code(400)
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Type", "application/json")
                .body(r#"{"error": "Failed to read body", "runtime": "wasmtime"}"#.to_string())
                .build();
        }
    };

    let game_data: Value = match serde_json::from_str(&body_str) {
        Ok(data) => data,
        Err(e) => {
            eprintln!("[ERROR] Failed to parse AI request: {}", e);
            return Response::builder()
                .status_code(400)
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Type", "application/json")
                .body(r#"{"error": "Invalid JSON", "runtime": "wasmtime"}"#.to_string())
                .build();
        }
    };

    // Wasmtime AI: Aggressive hunter strategy
    let action = get_wasmtime_ai_action(&game_data);
    
    let response = json!({
        "runtime": "wasmtime",
        "castle": "Temporal Sanctuary", 
        "action": action,
        "timestamp": get_timestamp()
    });

    Response::builder()
        .status_code(200)
        .header("Access-Control-Allow-Origin", "*")
        .header("Content-Type", "application/json")
        .body(response.to_string())
        .build()
}

fn get_wasmtime_ai_action(game_data: &Value) -> Value {
    // Wasmtime Temporal Sanctuary AI: Aggressive hunter
    // Strategy: Chase nearest opponent, try to cut them off
    
    let empty_map = serde_json::Map::new();
    let arena = game_data.get("arena").and_then(|a| a.as_object()).unwrap_or(&empty_map);
    let arena_size = arena.get("size").and_then(|s| s.as_u64()).unwrap_or(20) as f64;
    
    let empty_players_map = serde_json::Map::new();
    let players = game_data.get("players").and_then(|p| p.as_object()).unwrap_or(&empty_players_map);
    
    let empty_trails_map = serde_json::Map::new();
    let trails = game_data.get("trails").and_then(|t| t.as_object()).unwrap_or(&empty_trails_map);
    
    let my_player_id = "ai-wasmtime";
    
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
    let empty_pos_map = serde_json::Map::new();
    let my_pos = my_player.get("position").and_then(|p| p.as_object()).unwrap_or(&empty_pos_map);
    let my_x = my_pos.get("x").and_then(|x| x.as_f64()).unwrap_or(0.0);
    let my_z = my_pos.get("z").and_then(|z| z.as_f64()).unwrap_or(0.0);
    let my_direction = my_player.get("direction").and_then(|d| d.as_u64()).unwrap_or(0) as i32;
    
    // Find nearest alive opponent
    let mut nearest_opponent: Option<(&str, f64, f64)> = None;
    let mut min_distance = f64::MAX;
    
    for (player_id, player_data) in players.iter() {
        if player_id == my_player_id {
            continue;
        }
        
        if let Some(player_obj) = player_data.as_object() {
            let alive = player_obj.get("alive").and_then(|a| a.as_bool()).unwrap_or(true);
            if !alive {
                continue;
            }
            
            let empty_player_pos_map = serde_json::Map::new();
            let pos = player_obj.get("position").and_then(|p| p.as_object()).unwrap_or(&empty_player_pos_map);
            let x = pos.get("x").and_then(|x| x.as_f64()).unwrap_or(0.0);
            let z = pos.get("z").and_then(|z| z.as_f64()).unwrap_or(0.0);
            
            let distance = ((x - my_x).powi(2) + (z - my_z).powi(2)).sqrt();
            if distance < min_distance {
                min_distance = distance;
                nearest_opponent = Some((player_id, x, z));
            }
        }
    }
    
    // Check immediate danger ahead
    let (next_x, next_z) = calculate_next_pos(my_x, my_z, my_direction);
    if is_pos_dangerous(next_x, next_z, trails, arena_size) {
        // Immediate danger - turn to avoid
        let left_dir = (my_direction - 1 + 4) % 4;
        let right_dir = (my_direction + 1) % 4;
        
        let (left_x, left_z) = calculate_next_pos(my_x, my_z, left_dir);
        let (right_x, right_z) = calculate_next_pos(my_x, my_z, right_dir);
        
        let left_safe = !is_pos_dangerous(left_x, left_z, trails, arena_size);
        let right_safe = !is_pos_dangerous(right_x, right_z, trails, arena_size);
        
        if left_safe && right_safe {
            // Both safe - choose based on opponent position
            if let Some((_, opp_x, opp_z)) = nearest_opponent {
                let left_dist = ((left_x - opp_x).powi(2) + (left_z - opp_z).powi(2)).sqrt();
                let right_dist = ((right_x - opp_x).powi(2) + (right_z - opp_z).powi(2)).sqrt();
                
                if left_dist < right_dist {
                    return json!({
                        "type": "turn",
                        "direction": "left",
                        "reason": "avoid_and_hunt_left"
                    });
                } else {
                    return json!({
                        "type": "turn", 
                        "direction": "right",
                        "reason": "avoid_and_hunt_right"
                    });
                }
            } else {
                return json!({
                    "type": "turn",
                    "direction": "left",
                    "reason": "avoid_collision_left"
                });
            }
        } else if left_safe {
            return json!({
                "type": "turn",
                "direction": "left",
                "reason": "avoid_collision_only_left_safe"
            });
        } else if right_safe {
            return json!({
                "type": "turn",
                "direction": "right", 
                "reason": "avoid_collision_only_right_safe"
            });
        } else {
            // Desperate - just turn
            return json!({
                "type": "turn",
                "direction": "right",
                "reason": "desperate_turn"
            });
        }
    }
    
    // No immediate danger - hunt nearest opponent
    if let Some((_, opp_x, opp_z)) = nearest_opponent {
        // Calculate direction to opponent
        let dx = opp_x - my_x;
        let dz = opp_z - my_z;
        
        // Determine best turn to get closer to opponent
        let target_direction = if dx.abs() > dz.abs() {
            if dx > 0.0 { 1 } else { 3 }  // East or West
        } else {
            if dz < 0.0 { 0 } else { 2 }  // North or South  
        };
        
        if target_direction != my_direction {
            // Need to turn toward target
            let turn_diff = (target_direction - my_direction + 4) % 4;
            if turn_diff == 1 {
                return json!({
                    "type": "turn",
                    "direction": "right",
                    "reason": "hunting_opponent_right"
                });
            } else if turn_diff == 3 {
                return json!({
                    "type": "turn",
                    "direction": "left", 
                    "reason": "hunting_opponent_left"
                });
            }
        }
    }
    
    // Default: continue forward
    json!({
        "type": "move",
        "direction": "forward",
        "reason": "continuing_hunt"
    })
}

fn calculate_next_pos(x: f64, z: f64, direction: i32) -> (f64, f64) {
    match direction {
        0 => (x, z - 1.0),  // North
        1 => (x + 1.0, z),  // East
        2 => (x, z + 1.0),  // South
        3 => (x - 1.0, z),  // West
        _ => (x, z)
    }
}

fn is_pos_dangerous(x: f64, z: f64, trails: &serde_json::Map<String, Value>, arena_size: f64) -> bool {
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

// Helper functions for AI and general utilities
fn get_timestamp() -> u64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis() as u64
}

fn base64_encode(input: &str) -> String {
    // Simple base64 encoding for WASM environment
    // Using a basic implementation since we can't use external crates easily in WASM
    let chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut result = String::new();
    let bytes = input.as_bytes();
    
    for chunk in bytes.chunks(3) {
        let mut buf = [0u8; 3];
        for (i, &byte) in chunk.iter().enumerate() {
            buf[i] = byte;
        }
        
        let b64_1 = (buf[0] >> 2) & 0x3F;
        let b64_2 = ((buf[0] & 0x03) << 4) | ((buf[1] >> 4) & 0x0F);
        let b64_3 = ((buf[1] & 0x0F) << 2) | ((buf[2] >> 6) & 0x03);
        let b64_4 = buf[2] & 0x3F;
        
        result.push(chars.chars().nth(b64_1 as usize).unwrap());
        result.push(chars.chars().nth(b64_2 as usize).unwrap());
        
        if chunk.len() > 1 {
            result.push(chars.chars().nth(b64_3 as usize).unwrap());
        } else {
            result.push('=');
        }
        
        if chunk.len() > 2 {
            result.push(chars.chars().nth(b64_4 as usize).unwrap());
        } else {
            result.push('=');
        }
    }
    
    result
}

fn extract_json_field(json_str: &str, field_name: &str) -> Option<String> {
    // Simple JSON field extraction for WASM environment
    // This is a basic implementation - in production you'd use serde_json
    let field_pattern = format!("\"{}\":", field_name);
    
    if let Some(start) = json_str.find(&field_pattern) {
        let value_start = start + field_pattern.len();
        let remaining = &json_str[value_start..];
        
        // Skip whitespace
        let remaining = remaining.trim_start();
        
        if remaining.starts_with('"') {
            // String value
            let end_quote = remaining[1..].find('"')?;
            return Some(remaining[1..end_quote + 1].to_string());
        } else {
            // Non-string value (number, boolean, etc.)
            let mut end = 0;
            for (i, ch) in remaining.char_indices() {
                if ch == ',' || ch == '}' || ch == ']' || ch.is_whitespace() {
                    end = i;
                    break;
                }
                end = i + 1;
            }
            if end > 0 {
                return Some(remaining[..end].trim().to_string());
            }
        }
    }
    None
}
