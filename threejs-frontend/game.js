// WasiCycles Game - Cross-Runtime Multiplayer Light Cycle

// Runtime connection settings
const RUNTIMES = {
  wasmer: { 
    name: 'Wasmer Cycle', 
    url: 'http://localhost:8070', 
    color: '#00ccff',  // Classic Tron blue - darker and more electric
    ready: false,
    player: null
  },
  wasmedge: { 
    name: 'WASMEdge Cycle', 
    url: 'http://localhost:8083', 
    color: '#4ecdc4',
    ready: false,
    player: null
  },
  wasmtime: { 
    name: 'Wasmtime Cycle', 
    url: 'http://localhost:8090', 
    color: '#45b7d1',
    ready: false,
    player: null
  }
};

// Game state
let playerId = null;
let playerName = null;
let playerEmail = null;
let selectedRuntime = 'wasmer'; // Default to wasmer
let gameId = 'wasicycles-' + Math.random().toString(36).substr(2, 9);
let gameActive = false;
let gameScene = null;
let isFirstPersonView = false;

// Three.js game objects
let scene, camera, renderer, gameField;
let players = new Map(); // Map of playerId -> player object
let lightTrails = new Map(); // Map of playerId -> trail geometry
let localPlayer = null; // Reference to the local player for first-person view
let aiPlayers = []; // AI opponents
let cameraMode = 'overview'; // 'overview', 'first-person'
let lastMoveTime = 0;
let lastAIMoveTime = 0;

// AI strategies for single-player mode
const AI_STRATEGIES = {
  aggressive: {
    name: 'WASMEdge AI',
    color: 0xffd700,  // Gold color
    description: 'Aggressive - tries to cut off your path',
    runtime: 'wasmedge'
  },
  defensive: {
    name: 'Wasmtime AI', 
    color: 0xff8c00,  // Orange color
    description: 'Defensive - plays it safe',
    runtime: 'wasmtime'
  }
};

// Game constants
const GAME_CONFIG = {
  FIELD_SIZE: 200,  // Back to working field size
  GRID_SIZE: 40,    // Keep the larger grid spacing
  PLAYER_SIZE: 4,   // Keep bigger players
  TRAIL_HEIGHT: 4,  // Back to working trail height
  MOVE_SPEED: 8,    // Much smaller movement for smoother grid-based movement
  MOVE_INTERVAL: 800, // Keep slower movement
  AI_MOVE_INTERVAL: 1000 // Keep AI timing
};

// Spring Boot coordination
const SPRING_BOOT_URL = 'http://localhost:8050';

// ORDS REST API configuration (replace with your actual ORDS URL)
// TODO: Update this with your actual Oracle Cloud ORDS URL
// Format: https://your-apex-url/ords/admin/wasicycles
const ORDS_BASE_URL = 'https://your-oracle-cloud-url/ords/admin/wasicycles';

// For testing, we'll also try the Spring Boot fallback
const USE_SPRING_BOOT_FALLBACK = true;

// MongoDB API configuration (for Spring Boot integration)
const MONGODB_API_URL = 'http://localhost:8050/api/players/leaderboard';

// DOM elements
document.addEventListener('DOMContentLoaded', () => {
  // Initialize Three.js game
  initThreeJSGame();
  
  // Initialize UI
  updateRuntimeStatus();
  
  // Set up event listeners
  document.getElementById('join-game').addEventListener('click', startSinglePlayerGame);
  document.getElementById('leave-game').addEventListener('click', leaveGame);
  
  // Setup keyboard controls - this will handle all key events including camera toggle
  // document.addEventListener('keydown', handleKeyPress); // Removed duplicate handler
  
  // Check runtime status every 5 seconds
  setInterval(updateRuntimeStatus, 5000);
  
  // Start game update loop
  startGameLoop();
  
  // Load initial leaderboard
  loadLeaderboard();
});

// Database Integration Functions

// Insert player into database via ORDS or Spring Boot fallback
async function insertPlayer(playerId, playerName, playerEmail, runtime, tshirtSize = 'M') {
  // First try ORDS if URL is configured
  if (!ORDS_BASE_URL.includes('your-oracle-cloud-url') && !USE_SPRING_BOOT_FALLBACK) {
    try {
      const playerData = {
        player_name: playerName,
        player_email: playerEmail,
        player_score: 0,
        runtime: runtime,
        tshirtsize: tshirtSize
      };
      
      console.log('🔧 Sending player data to ORDS:', JSON.stringify(playerData, null, 2));
      console.log(`🔧 ORDS URL: ${ORDS_BASE_URL}/player/${playerId}`);
      
      const response = await fetch(`${ORDS_BASE_URL}/player/${playerId}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: JSON.stringify(playerData)
      });
      
      if (response.ok) {
        const result = await response.text();
        console.log(`✅ Player ${playerId} inserted via ORDS. Response:`, result);
        return true;
      } else {
        const errorText = await response.text();
        console.warn(`⚠️ ORDS failed: ${response.status} ${response.statusText}`, errorText);
      }
    } catch (error) {
      console.error('❌ ORDS error:', error);
    }
  }
  
  // Fallback to Spring Boot
  try {
    const playerData = {
      playerId: playerId,
      playerName: playerName,
      playerEmail: playerEmail,
      playerScore: 0,
      runtime: runtime,
      tshirtSize: tshirtSize
    };
    
    console.log('🔧 Fallback: Sending player data to Spring Boot:', JSON.stringify(playerData, null, 2));
    
    const response = await fetch(`${SPRING_BOOT_URL}/api/players`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      body: JSON.stringify(playerData)
    });
    
    if (response.ok) {
      const result = await response.json();
      console.log(`✅ Player ${playerId} inserted via Spring Boot:`, result);
      return true;
    } else {
      const errorText = await response.text();
      console.warn(`⚠️ Spring Boot failed: ${response.status} ${response.statusText}`, errorText);
      return false;
    }
  } catch (error) {
    console.error('❌ Spring Boot error:', error);
    return false;
  }
}

// Increment player score via ORDS or Spring Boot fallback (for victories)
async function incrementPlayerScore(playerId) {
  // First try ORDS if URL is configured  
  if (!ORDS_BASE_URL.includes('your-oracle-cloud-url') && !USE_SPRING_BOOT_FALLBACK) {
    try {
      console.log(`🏆 Attempting to increment score via ORDS for player: ${playerId}`);
      console.log(`🔧 ORDS URL: ${ORDS_BASE_URL}/player/${playerId}/increment-score`);
      
      const response = await fetch(`${ORDS_BASE_URL}/player/${playerId}/increment-score`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        }
      });
      
      if (response.ok) {
        const result = await response.text();
        console.log(`🏆 Score incremented via ORDS for player ${playerId}. Response:`, result);
        loadLeaderboard(); // Refresh leaderboard
        return true;
      } else {
        const errorText = await response.text();
        console.warn(`⚠️ ORDS increment failed: ${response.status} ${response.statusText}`, errorText);
      }
    } catch (error) {
      console.error('❌ ORDS increment error:', error);
    }
  }
  
  // Fallback: increment score via Spring Boot
  try {
    console.log(`🏆 Fallback: Attempting to increment score via Spring Boot for player: ${playerId}`);
    
    // Spring Boot endpoint to increment score (we'll need to create this)
    const response = await fetch(`${SPRING_BOOT_URL}/api/players/${playerId}/increment-score`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      }
    });
    
    if (response.ok) {
      const result = await response.json();
      console.log(`🏆 Score incremented via Spring Boot for player ${playerId}:`, result);
      loadLeaderboard(); // Refresh leaderboard
      return true;
    } else {
      const errorText = await response.text();
      console.warn(`⚠️ Spring Boot increment failed: ${response.status} ${response.statusText}`, errorText);
      return false;
    }
  } catch (error) {
    console.error('❌ Spring Boot increment error:', error);
    return false;
  }
}

// Load leaderboard via Spring Boot MongoDB API
async function loadLeaderboard() {
  try {
    const response = await fetch(MONGODB_API_URL, {
      method: 'GET',
      headers: {
        'Accept': 'application/json'
      }
    });
    
    if (response.ok) {
      const leaderboardData = await response.json();
      displayLeaderboard(leaderboardData);
      console.log('📊 Leaderboard loaded successfully');
    } else {
      console.warn(`⚠️ Failed to load leaderboard: ${response.statusText}`);
      // Fallback to ORDS direct access
      loadLeaderboardFromORDS();
    }
  } catch (error) {
    console.error('❌ Error loading leaderboard:', error);
    // Fallback to ORDS direct access
    loadLeaderboardFromORDS();
  }
}

// Fallback: Load leaderboard directly from ORDS
async function loadLeaderboardFromORDS() {
  try {
    const response = await fetch(`${ORDS_BASE_URL}/leaderboard`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json'
      }
    });
    
    if (response.ok) {
      const data = await response.json();
      const leaderboardData = data.items || data; // Handle different response formats
      displayLeaderboard(leaderboardData);
      console.log('📊 Leaderboard loaded from ORDS');
    } else {
      console.warn(`⚠️ Failed to load leaderboard from ORDS: ${response.statusText}`);
    }
  } catch (error) {
    console.error('❌ Error loading leaderboard from ORDS:', error);
  }
}

// Display leaderboard in the UI
function displayLeaderboard(leaderboardData) {
  const leaderboardBody = document.getElementById('leaderboard-body');
  if (!leaderboardBody) return;
  
  leaderboardBody.innerHTML = '';
  
  if (!leaderboardData || leaderboardData.length === 0) {
    leaderboardBody.innerHTML = '<tr><td colspan="4">No players yet</td></tr>';
    return;
  }
  
  leaderboardData.slice(0, 10).forEach((player, index) => {
    const row = document.createElement('tr');
    
    // Handle both MongoDB API format and ORDS format
    const name = player.playerName || player.player_name || 'Unknown';
    const score = player.playerScore || player.player_score || 0;
    const runtime = player.runtime || 'N/A';
    
    row.innerHTML = `
      <td>${index + 1}</td>
      <td>${name}</td>
      <td>${score}</td>
      <td style="color: ${getRuntimeColor(runtime)}">${runtime}</td>
    `;
    leaderboardBody.appendChild(row);
  });
}

// Get color for runtime display
function getRuntimeColor(runtime) {
  switch(runtime?.toLowerCase()) {
    case 'wasmer': return '#00ccff';
    case 'wasmedge': return '#ffd700';
    case 'wasmtime': return '#ff8c00';
    default: return '#ffffff';
  }
}

// Initialize Three.js 3D game
function initThreeJSGame() {
  console.log('🎮 Initializing Three.js game...');
  
  // Create scene
  scene = new THREE.Scene();
  scene.fog = new THREE.Fog(0x000000, 50, 200);
  console.log('✅ Scene created');
  
  // Create first-person camera (WebXR compatible)
  camera = new THREE.PerspectiveCamera(75, 1400/1000, 0.1, 1000);
  // Start with better overview position to see the field clearly
  camera.position.set(0, 120, 80);
  camera.lookAt(0, 0, 0);
  console.log('✅ Camera positioned at:', camera.position);
  
  // Create renderer with WebXR support
  const gameCanvas = document.getElementById('game-canvas');
  console.log('Canvas element:', gameCanvas);
  renderer = new THREE.WebGLRenderer({ canvas: gameCanvas, antialias: true });
  renderer.setSize(1400, 1000);
  renderer.setClearColor(0x000033, 1); // Slightly brighter background
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  console.log('✅ Renderer created with size: 1400x1000');
  
  // Enable WebXR for VR/AR support
  renderer.xr.enabled = true;
  
  // Create game field (grid)
  createGameField();
  
  // Add lights
  const ambientLight = new THREE.AmbientLight(0x404040, 0.6); // Much brighter ambient
  scene.add(ambientLight);
  
  const directionalLight = new THREE.DirectionalLight(0x00ccff, 1.5); // Brighter directional
  directionalLight.position.set(10, 50, 5);
  directionalLight.castShadow = true;
  directionalLight.shadow.camera.near = 0.1;
  directionalLight.shadow.camera.far = 200;
  directionalLight.shadow.camera.left = -50;
  directionalLight.shadow.camera.right = 50;
  directionalLight.shadow.camera.top = 50;
  directionalLight.shadow.camera.bottom = -50;
  scene.add(directionalLight);
  
  // Add secondary light for better visibility
  const secondLight = new THREE.DirectionalLight(0xffffff, 0.8);
  secondLight.position.set(-20, 60, -10);
  scene.add(secondLight);
  
  console.log('✅ Enhanced lighting setup complete');
  
  // Initial render
  renderer.render(scene, camera);
  
  // Setup WebXR button if available
  setupWebXR();
}

// Create the game field (Tron-style grid)
function createGameField() {
  const fieldGeometry = new THREE.PlaneGeometry(GAME_CONFIG.FIELD_SIZE, GAME_CONFIG.FIELD_SIZE);
  const fieldMaterial = new THREE.MeshLambertMaterial({ 
    color: 0x002266, 
    transparent: false,
    opacity: 1.0
  });
  gameField = new THREE.Mesh(fieldGeometry, fieldMaterial);
  gameField.rotation.x = -Math.PI / 2;
  gameField.receiveShadow = true;
  scene.add(gameField);
  
  // Add grid lines  
  const gridHelper = new THREE.GridHelper(GAME_CONFIG.FIELD_SIZE, 50, 0x00ddff, 0x004488);
  gridHelper.position.y = 0.01; // Slightly above the field
  scene.add(gridHelper);
  
  console.log('✅ Game field created:', GAME_CONFIG.FIELD_SIZE, 'x', GAME_CONFIG.FIELD_SIZE);
  
  // Reference cubes removed - they served no purpose and were just visual clutter
  
  // Add field boundaries (walls)
  createFieldBoundaries();
}

// Create boundary walls
function createFieldBoundaries() {
  const wallHeight = 15;  // Much taller walls for better visibility
  const wallThickness = 2; // Slightly thicker walls
  const wallMaterial = new THREE.MeshLambertMaterial({ 
    color: 0x00ccff,
    emissive: 0x001144,
    transparent: true,
    opacity: 0.8  // Slight transparency so they don't completely block view
  });
  
  // Create 4 walls
    const walls = [
    { x: 0, z: GAME_CONFIG.FIELD_SIZE/2, w: GAME_CONFIG.FIELD_SIZE, h: wallThickness }, // North wall
    { x: 0, z: -GAME_CONFIG.FIELD_SIZE/2, w: GAME_CONFIG.FIELD_SIZE, h: wallThickness }, // South wall
    { x: GAME_CONFIG.FIELD_SIZE/2, z: 0, w: wallThickness, h: GAME_CONFIG.FIELD_SIZE }, // East wall
    { x: -GAME_CONFIG.FIELD_SIZE/2, z: 0, w: wallThickness, h: GAME_CONFIG.FIELD_SIZE } // West wall
  ];
  
  walls.forEach(pos => {
    const wallGeometry = new THREE.BoxGeometry(pos.w, wallHeight, pos.h);
    const wall = new THREE.Mesh(wallGeometry, wallMaterial);
    wall.position.set(pos.x, wallHeight/2, pos.z);
    wall.castShadow = true;
    scene.add(wall);
  });
}

// Create a new player light cycle
function createPlayer(playerId, runtime, position = { x: 0, z: 0 }, direction = 'north', customColor = null) {
  const playerColor = customColor || RUNTIMES[runtime]?.color || '#ffffff';
  const isLocalPlayer = (playerId === window.playerId);
  
  // Create player geometry (light cycle)
  const playerGeometry = new THREE.BoxGeometry(GAME_CONFIG.PLAYER_SIZE, GAME_CONFIG.PLAYER_SIZE, GAME_CONFIG.PLAYER_SIZE * 2); // Make cycles longer
  const playerMaterial = new THREE.MeshLambertMaterial({ 
    color: playerColor,
    emissive: playerColor,
    emissiveIntensity: 0.3
  });
  
  const playerMesh = new THREE.Mesh(playerGeometry, playerMaterial);
  playerMesh.position.set(position.x, GAME_CONFIG.PLAYER_SIZE/2, position.z);
  playerMesh.castShadow = true;
  
  // Add glow effect
  const glowGeometry = new THREE.SphereGeometry(GAME_CONFIG.PLAYER_SIZE * 0.8, 16, 16);
  const glowMaterial = new THREE.MeshBasicMaterial({
    color: playerColor,
    transparent: true,
    opacity: 0.3
  });
  const glow = new THREE.Mesh(glowGeometry, glowMaterial);
  glow.position.copy(playerMesh.position);
  
  scene.add(playerMesh);
  scene.add(glow);
  
  // Create direction vector
  const directionVector = getDirectionVector(direction);
  
    // Create player object
  const player = {
    id: playerId,
    playerId: playerId, // Add this for consistency
    runtime: runtime,
    mesh: playerMesh,
    glow: glow,
    position: { x: position.x, z: position.z },
    direction: direction,
    directionVector: directionVector,
    trail: [],
    color: playerColor,
    alive: true,
    isLocal: isLocalPlayer
  };
  
  players.set(playerId, player);
  
  // Set local player reference
  if (isLocalPlayer) {
    localPlayer = player;
    localPlayer.playerId = playerId; // Ensure playerId is set
    // Stay in overview mode initially so we can see everything
    // User can press 'C' to switch to first-person
    console.log(`✅ Created human player: ${playerId}`);
    console.log('Player position:', player.position);
    console.log('Player mesh position:', player.mesh.position);
    console.log('📷 Press "C" to switch camera modes');
  }
  
  // Initialize light trail for this player
  console.log(`🔧 About to initialize trail for player: ${playerId} with color: ${playerColor}`);
  initializePlayerTrail(playerId, playerColor);
  
  // Add initial trail point at starting position
  console.log(`🔧 About to add initial trail segment for player: ${playerId} at (${position.x}, ${position.z})`);
  addTrailSegment(position.x, position.z, playerId);
  
  return player;
}

// Check for collisions with trails or walls
function checkCollision(x, z, playerId) {
  const collisionRadius = 3; // Distance for collision detection
  
  // Check wall collisions first
  const boundary = GAME_CONFIG.FIELD_SIZE / 2 - 5; // Wall collision boundary
  if (x <= -boundary || x >= boundary || z <= -boundary || z >= boundary) {
    console.log(`💥 Player ${playerId} hit a wall at (${x}, ${z})`);
    return { type: 'wall', position: { x, z } };
  }
  
  // Check trail collisions - compare with all trail segments from all players
  for (const [trailPlayerId, trail] of lightTrails.entries()) {
    if (trail.points && trail.points.length > 0) {
      for (const point of trail.points) {
        const distance = Math.sqrt(
          Math.pow(x - point.x, 2) + Math.pow(z - point.z, 2)
        );
        
        if (distance < collisionRadius) {
          console.log(`💥 Player ${playerId} hit trail from ${trailPlayerId} at (${x}, ${z})`);
          return { 
            type: 'trail', 
            position: { x, z }, 
            hitPlayer: trailPlayerId 
          };
        }
      }
    }
  }
  
  return null; // No collision
}

// Remove a crashed player and their trail
function crashPlayer(playerId, collisionType = 'unknown') {
  const player = players.get(playerId);
  if (!player) return;
  
  console.log(`🔥 Player ${playerId} crashed due to ${collisionType}!`);
  
  // Mark player as crashed
  player.alive = false;
  
  // Add crash effect (explosion-like visual)
  if (player.mesh) {
    // Change color to indicate crash
    player.mesh.material.color.setHex(0xff0000);
    player.mesh.material.emissive.setHex(0xff0000);
    player.mesh.material.emissiveIntensity = 0.8;
    
    // Remove after short delay
    setTimeout(() => {
      scene.remove(player.mesh);
      if (player.glow) scene.remove(player.glow);
    }, 1000);
  }
  
  // Remove trail segments
  const trail = lightTrails.get(playerId);
  if (trail && trail.segments) {
    trail.segments.forEach(segment => {
      scene.remove(segment);
    });
    lightTrails.delete(playerId);
  }
  
  // Check if game should end
  if (playerId === localPlayer?.playerId) {
    setTimeout(() => endGame('crash'), 1500);
  } else {
    // AI player crashed - check if player won
    const aliveAI = aiPlayers.filter(ai => ai.alive);
    if (aliveAI.length === 0) {
      setTimeout(() => endGame('victory'), 1500);
    }
  }
}
function getDirectionVector(direction) {
  switch(direction) {
    case 'north': return { x: 0, z: -1 };
    case 'south': return { x: 0, z: 1 };
    case 'east': return { x: 1, z: 0 };
    case 'west': return { x: -1, z: 0 };
    default: return { x: 0, z: -1 };
  }
}

// Initialize light trail for a player
function initializePlayerTrail(playerId, color) {
  console.log(`🔧 Initializing trail for player: ${playerId}`);
  
  // Create multiple trail segments as boxes for better visibility
  const trailSegments = [];
  
  lightTrails.set(playerId, {
    segments: trailSegments,
    points: [],
    color: color
  });
  
  console.log(`🌟 Trail initialized for player: ${playerId} with color: ${color}`, lightTrails.get(playerId));
}

// Add a trail segment for a player
function addTrailSegment(x, z, playerId) {
  console.log(`🔧 Adding trail segment for player: ${playerId} at (${x}, ${z})`);
  
  const trail = lightTrails.get(playerId);
  if (trail) {
    // Create a visible trail segment as a box
    const segmentGeometry = new THREE.BoxGeometry(2, 1, 2);
    const segmentMaterial = new THREE.MeshLambertMaterial({ 
      color: trail.color,
      emissive: trail.color,
      emissiveIntensity: 0.3,
      transparent: true,
      opacity: 0.8
    });
    
    const segment = new THREE.Mesh(segmentGeometry, segmentMaterial);
    segment.position.set(x, 0.5, z);
    scene.add(segment);
    
    trail.segments.push(segment);
    trail.points.push(new THREE.Vector3(x, 0.5, z));
    
    console.log(`✨ Trail segment added for ${playerId}: ${trail.segments.length} segments total`);
  } else {
    console.warn(`⚠️ No trail found for player: ${playerId}`);
  }
}

// Update player position and trail
function updatePlayerPosition(playerId, newPosition, direction) {
  const player = players.get(playerId);
  if (!player || !player.alive) return;
  
  // Update player position
  const oldPosition = { ...player.position };
  player.position = { x: newPosition.x, z: newPosition.z };
  player.direction = direction;
  player.directionVector = getDirectionVector(direction);
  
  // Update 3D mesh position
  player.mesh.position.set(newPosition.x, GAME_CONFIG.PLAYER_SIZE/2, newPosition.z);
  player.glow.position.copy(player.mesh.position);
  
  // Orient the cycle to face the movement direction
  const angle = Math.atan2(player.directionVector.x, player.directionVector.z);
  player.mesh.rotation.y = -angle;
  
  // Add to trail
  const trail = lightTrails.get(playerId);
  if (trail) {
    trail.points.push(new THREE.Vector3(oldPosition.x, 0.2, oldPosition.z));
    trail.points.push(new THREE.Vector3(newPosition.x, 0.2, newPosition.z));
    
    // Update trail geometry
    if (trail.points.length > 0) {
      trail.geometry.setFromPoints(trail.points);
      trail.geometry.needsUpdate = true;
    }
  }
}

// Toggle between first-person and overview camera
function toggleFirstPersonCamera() {
  if (localPlayer) {
    cameraMode = cameraMode === 'overview' ? 'first-person' : 'overview';
    console.log(`📷 Camera switched to: ${cameraMode}`);
  } else {
    console.log('📷 No local player - camera toggle unavailable');
  }
}

// Update camera based on mode
function updateCamera() {
  if (cameraMode === 'first-person' && localPlayer && localPlayer.alive) {
    // First-person camera that rotates with the player's direction
    const player = localPlayer;
    
    // Ensure direction vector exists, fallback to north
    const dirVec = player.directionVector || { x: 0, z: -1 };
    
    // Calculate camera position relative to player's facing direction
    // Camera stays behind the cycle at the same distance and height
    const cameraDistance = 20; // Distance behind the player
    const cameraHeight = 15;   // Height above the ground
    
    // Position camera behind the player based on their current direction
    const cameraPosition = {
      x: player.position.x - dirVec.x * cameraDistance,
      y: cameraHeight,
      z: player.position.z - dirVec.z * cameraDistance
    };
    
    camera.position.set(cameraPosition.x, cameraPosition.y, cameraPosition.z);
    
    // Camera looks directly ahead in the player's movement direction
    // Look ahead point in front of the player
    const lookAheadDistance = 30;
    const lookAtPoint = {
      x: player.position.x + dirVec.x * lookAheadDistance,
      y: 5, // Look slightly above ground level
      z: player.position.z + dirVec.z * lookAheadDistance
    };
    
    camera.lookAt(lookAtPoint.x, lookAtPoint.y, lookAtPoint.z);
    
    // Debug the camera orientation
    console.log(`📷 First-person camera: player at (${player.position.x}, ${player.position.z}), facing ${player.direction}, camera at (${cameraPosition.x}, ${cameraPosition.z}), looking at (${lookAtPoint.x}, ${lookAtPoint.z})`);
    
  } else if (cameraMode === 'overview') {
    // Overview camera - back to working height
    camera.position.set(0, 80, 50);
    camera.lookAt(0, 0, 0);
  }
}

// Setup WebXR functionality
function setupWebXR() {
  if ('xr' in navigator) {
    navigator.xr.isSessionSupported('immersive-vr').then(supported => {
      if (supported) {
        // Add VR button to the UI (if we have one)
        const vrBtn = document.getElementById('vr-button');
        if (vrBtn) {
          vrBtn.style.display = 'block';
          vrBtn.addEventListener('click', toggleVR);
        }
      }
    });
  }
}

// Toggle VR mode
async function toggleVR() {
  if (renderer.xr.isPresenting) {
    renderer.xr.getSession().end();
  } else {
    try {
      const session = await navigator.xr.requestSession('immersive-vr');
      renderer.xr.setSession(session);
      cameraMode = 'vr'; // Special VR camera mode
    } catch (error) {
      console.warn('VR not available:', error);
    }
  }
}

// Remove player from game
function removePlayer(playerId) {
  const player = players.get(playerId);
  if (player) {
    scene.remove(player.mesh);
    scene.remove(player.glow);
    players.delete(playerId);
  }
  
  const trail = lightTrails.get(playerId);
  if (trail) {
    // Remove all trail segments
    trail.segments.forEach(segment => {
      scene.remove(segment);
    });
    lightTrails.delete(playerId);
  }
}

// Start the game update loop
function startGameLoop() {
  function gameLoop() {
    if (gameActive && selectedRuntime) {
      // Poll for game state updates from Spring Boot (for multiplayer mode)
      // pollGameStateUpdates();
      
      // Movement is now handled by the grid-based system in setupKeyboardControls
    }
    
    // Update camera based on current mode
    updateCamera();
    
    // Render the scene
    renderer.render(scene, camera);
    
    requestAnimationFrame(gameLoop);
  }
  
  // Use WebXR-compatible animation loop
  renderer.setAnimationLoop(gameLoop);
  
  // Set up keyboard controls
  setupKeyboardControls();
}

// Keyboard input handling - Single Player Grid-Based Movement
function setupKeyboardControls() {
  let currentDirection = null;
  let pendingDirection = null;
  
  // Direction mapping for grid movement
  const directions = {
    'north': { x: 0, z: -GAME_CONFIG.MOVE_SPEED },
    'south': { x: 0, z: GAME_CONFIG.MOVE_SPEED },
    'west': { x: -GAME_CONFIG.MOVE_SPEED, z: 0 },
    'east': { x: GAME_CONFIG.MOVE_SPEED, z: 0 }
  };

  const opposites = {
    'north': 'south',
    'south': 'north', 
    'east': 'west',
    'west': 'east'
  };
  
  document.addEventListener('keydown', (event) => {
    if (!gameActive || !localPlayer) return;
    
    const key = event.key.toLowerCase();
    
    // Camera toggle
    if (key === 'c') {
      toggleFirstPersonCamera();
      return;
    }
    
    // Movement keys - adjust based on camera mode
    let newDirection = null;
    
    if (cameraMode === 'first-person' && localPlayer) {
      // In first-person mode, controls are relative to player's current facing direction
      const currentDir = localPlayer.direction || 'north';
      
      switch(event.code) {
        case 'ArrowUp':
        case 'KeyW':
          // Forward = continue in current direction
          newDirection = currentDir;
          break;
        case 'ArrowDown': 
        case 'KeyS':
          // Backward = turn around (opposite direction)
          const opposites = { 'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east' };
          newDirection = opposites[currentDir];
          break;
        case 'ArrowLeft':
        case 'KeyA':
          // Left = turn left relative to current direction
          const leftTurns = { 'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north' };
          newDirection = leftTurns[currentDir];
          break;
        case 'ArrowRight':
        case 'KeyD':
          // Right = turn right relative to current direction
          const rightTurns = { 'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north' };
          newDirection = rightTurns[currentDir];
          break;
      }
    } else {
      // In overview mode, controls are absolute world directions
      switch(event.code) {
        case 'ArrowUp':
        case 'KeyW':
          newDirection = 'north';
          break;
        case 'ArrowDown': 
        case 'KeyS':
          newDirection = 'south';
          break;
        case 'ArrowLeft':
        case 'KeyA':
          newDirection = 'west';
          break;
        case 'ArrowRight':
        case 'KeyD':
          newDirection = 'east';
          break;
      }
    }
    
    // Queue direction change (no 180-degree turns)
    if (newDirection && newDirection !== opposites[currentDirection]) {
      pendingDirection = newDirection;
    }
  });

  // Grid-based movement function
  function processMovement() {
    const now = Date.now();
    
    // Player movement
    if (now - lastMoveTime >= GAME_CONFIG.MOVE_INTERVAL) {
      if (pendingDirection) {
        currentDirection = pendingDirection;
        pendingDirection = null;
      }
      
      if (currentDirection && localPlayer) {
        const direction = directions[currentDirection];
        const newX = localPlayer.position.x + direction.x;
        const newZ = localPlayer.position.z + direction.z;
        
        // Check for collisions before moving
        const collision = checkCollision(newX, newZ, localPlayer.playerId);
        if (collision) {
          crashPlayer(localPlayer.playerId, collision.type);
          return; // Stop processing movement
        }
        
        // Check boundaries (backup check)
        const boundary = GAME_CONFIG.FIELD_SIZE / 2 - 10;
        if (newX >= -boundary && newX <= boundary && newZ >= -boundary && newZ <= boundary) {
          localPlayer.position.x = newX;
          localPlayer.position.z = newZ;
          localPlayer.direction = currentDirection;
          
          // Update direction vector for camera orientation
          localPlayer.directionVector = getDirectionVector(currentDirection);
          
          // Update visual mesh position immediately for dramatic movement
          if (localPlayer.mesh) {
            localPlayer.mesh.position.set(newX, GAME_CONFIG.PLAYER_SIZE/2, newZ);
            
            // Orient the player mesh to face the movement direction
            const angle = Math.atan2(localPlayer.directionVector.x, localPlayer.directionVector.z);
            localPlayer.mesh.rotation.y = -angle;
          }
          if (localPlayer.glow) {
            localPlayer.glow.position.set(newX, GAME_CONFIG.PLAYER_SIZE/2, newZ);
          }
          
          addTrailSegment(newX, newZ, localPlayer.playerId);
          console.log(`🎮 Player moved to: (${newX}, ${newZ}), facing: ${currentDirection}, dirVector: (${localPlayer.directionVector.x}, ${localPlayer.directionVector.z})`);
        } else {
          console.log('🏁 Player hit boundary!');
          crashPlayer(localPlayer.playerId, 'boundary');
        }
      }
      lastMoveTime = now;
    }
    
    // AI movement
    if (now - lastAIMoveTime >= GAME_CONFIG.AI_MOVE_INTERVAL) {
      aiPlayers.forEach(aiPlayer => {
        moveAIPlayer(aiPlayer);
      });
      lastAIMoveTime = now;
    }
  }

  // Start the movement loop
  setInterval(processMovement, 50); // Check every 50ms
}

function moveAIPlayer(aiPlayer) {
  if (!aiPlayer || !gameActive) return;
  
  const directions = ['north', 'south', 'east', 'west'];
  const opposites = {
    'north': 'south', 'south': 'north', 
    'east': 'west', 'west': 'east'
  };
  
  // Get valid directions (not opposite to current)
  const validDirections = directions.filter(dir => 
    dir !== opposites[aiPlayer.direction]
  );
  
  // Simple AI: pick random valid direction with some strategy
  let newDirection;
  if (aiPlayer.strategy === 'aggressive') {
    // Aggressive: try to move toward player
    const dx = localPlayer.position.x - aiPlayer.position.x;
    const dz = localPlayer.position.z - aiPlayer.position.z;
    
    if (Math.abs(dx) > Math.abs(dz)) {
      newDirection = dx > 0 ? 'east' : 'west';
    } else {
      newDirection = dz > 0 ? 'south' : 'north';
    }
    
    // If that direction is invalid, pick random
    if (!validDirections.includes(newDirection)) {
      newDirection = validDirections[Math.floor(Math.random() * validDirections.length)];
    }
  } else {
    // Defensive: pick random valid direction
    newDirection = validDirections[Math.floor(Math.random() * validDirections.length)];
  }
  
  // Move AI player
  const directionMap = {
    'north': { x: 0, z: -GAME_CONFIG.MOVE_SPEED },
    'south': { x: 0, z: GAME_CONFIG.MOVE_SPEED },
    'west': { x: -GAME_CONFIG.MOVE_SPEED, z: 0 },
    'east': { x: GAME_CONFIG.MOVE_SPEED, z: 0 }
  };
  
  const direction = directionMap[newDirection];
  const newX = aiPlayer.position.x + direction.x;
  const newZ = aiPlayer.position.z + direction.z;
  
  // Check for collisions before moving AI
  const collision = checkCollision(newX, newZ, aiPlayer.playerId);
  if (collision) {
    crashPlayer(aiPlayer.playerId, collision.type);
    return; // Stop processing this AI movement
  }
  
  // Check boundaries
  const boundary = GAME_CONFIG.FIELD_SIZE / 2 - 10;
  if (newX >= -boundary && newX <= boundary && newZ >= -boundary && newZ <= boundary) {
    aiPlayer.position.x = newX;
    aiPlayer.position.z = newZ;
    aiPlayer.direction = newDirection;
    
    // Update direction vector for AI too
    aiPlayer.directionVector = getDirectionVector(newDirection);
    
    // Update visual mesh position for AI
    if (aiPlayer.mesh) {
      aiPlayer.mesh.position.set(newX, GAME_CONFIG.PLAYER_SIZE/2, newZ);
      
      // Orient the AI cycle to face the movement direction
      const angle = Math.atan2(aiPlayer.directionVector.x, aiPlayer.directionVector.z);
      aiPlayer.mesh.rotation.y = -angle;
    }
    if (aiPlayer.glow) {
      aiPlayer.glow.position.set(newX, GAME_CONFIG.PLAYER_SIZE/2, newZ);
    }
    
    addTrailSegment(newX, newZ, aiPlayer.playerId);
    console.log(`🤖 AI ${aiPlayer.strategy} moved to: (${newX}, ${newZ}), facing: ${newDirection}`);
  } else {
    // AI hit boundary
    crashPlayer(aiPlayer.playerId, 'boundary');
  }
}

// Update local player position for immediate feedback
function updateLocalPlayerPosition() {
  if (!localPlayer) return;
  
  const direction = localPlayer.direction;
  const speed = GAME_CONFIG.MOVE_SPEED;
  
  switch(direction) {
    case 'north':
      localPlayer.position.z -= speed;
      break;
    case 'south':
      localPlayer.position.z += speed;
      break;
    case 'east':
      localPlayer.position.x += speed;
      break;
    case 'west':
      localPlayer.position.x -= speed;
      break;
  }
  
  // Update the 3D object position
  if (localPlayer.mesh) {
    localPlayer.mesh.position.set(
      localPlayer.position.x,
      localPlayer.position.y,
      localPlayer.position.z
    );
  }
  
  // Keep player within bounds
  const bounds = GAME_CONFIG.FIELD_SIZE / 2;
  localPlayer.position.x = Math.max(-bounds, Math.min(bounds, localPlayer.position.x));
  localPlayer.position.z = Math.max(-bounds, Math.min(bounds, localPlayer.position.z));
}

async function sendPlayerMove(direction) {
  if (!selectedRuntime || !playerId) return;
  
  try {
    const runtimeKey = selectedRuntime.name.toLowerCase().replace(' cycle', '');
    let response;
    
    if (runtimeKey === 'wasmer') {
      // Wasmer uses different endpoint
      const kafkaData = {
        test_event: {
          type: 'player_move',
          player_id: playerId,
          game_id: gameId,
          runtime: runtimeKey,
          direction: direction,
          position: localPlayer.position,
          timestamp: Date.now()
        }
      };
      
      response = await fetch(`${selectedRuntime.url}/consume-persistent`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(kafkaData)
      });
    } else {
      // WASMEdge and Wasmtime use /move endpoint
      const moveData = {
        player_id: playerId,
        game_id: gameId,
        runtime: runtimeKey,
        direction: direction,
        position: localPlayer.position,
        timestamp: Date.now()
      };
      
      response = await fetch(`${selectedRuntime.url}/move`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(moveData)
      });
    }
    
    if (response.ok) {
      console.log(`🎮 Sent move: ${direction} for player ${playerId}`);
    } else {
      console.warn(`Failed to send move: ${response.statusText}`);
    }
  } catch (error) {
    console.error('Error sending player move:', error);
  }
}

// Throttle polling to prevent resource exhaustion
let lastPollTime = 0;
let pollErrorCount = 0;
const POLL_INTERVAL = 1000; // Poll every 1 second instead of every frame
const MAX_POLL_ERRORS = 5; // Stop polling after 5 consecutive errors

// Poll Spring Boot for game state updates (throttled)
async function pollGameStateUpdates() {
  const now = Date.now();
  
  // Throttle requests to once per second
  if (now - lastPollTime < POLL_INTERVAL) {
    return;
  }
  
  // Stop polling if too many consecutive errors
  if (pollErrorCount >= MAX_POLL_ERRORS) {
    console.warn('⚠️ Stopped polling due to repeated errors. Check Spring Boot server at', SPRING_BOOT_URL);
    return;
  }
  
  lastPollTime = now;
  
  try {
    const response = await fetch(`${SPRING_BOOT_URL}/cross-runtime/display-okafka-consumed-messages`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json'
      },
      signal: AbortSignal.timeout(3000) // 3 second timeout
    });
    
    if (response.ok) {
      const data = await response.json();
      processGameStateUpdates(data);
      pollErrorCount = 0; // Reset error count on success
    } else {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
  } catch (error) {
    pollErrorCount++;
    console.warn(`Failed to poll game state (${pollErrorCount}/${MAX_POLL_ERRORS}):`, error.message);
    
    if (pollErrorCount >= MAX_POLL_ERRORS) {
      console.error('🚨 Game state polling disabled. Please check Spring Boot server.');
    }
  }
}

// Process game state updates from Spring Boot
function processGameStateUpdates(data) {
  if (data.messages && Array.isArray(data.messages)) {
    // Process recent messages for player movements
    data.messages.slice(-20).forEach(message => { // Only process last 20 messages
      if (message.data && message.data.type === 'player_move') {
        const { player_id, position, direction, runtime } = message.data;
        
        if (!players.has(player_id)) {
          // Create new player
          createPlayer(player_id, runtime, position, direction);
        } else {
          // Update existing player
          updatePlayerPosition(player_id, position, direction);
        }
      } else if (message.data && message.data.type === 'player_crash') {
        const { player_id } = message.data;
        const player = players.get(player_id);
        if (player) {
          player.alive = false;
          // Could add explosion effect here
        }
      }
    });
  }
}

// Update the status of each runtime
async function updateRuntimeStatus() {
  console.log('🔄 Checking runtime status...');
  console.log('RUNTIMES object:', RUNTIMES);
  
  for (const [key, runtime] of Object.entries(RUNTIMES)) {
    console.log(`Processing runtime: ${key}`, runtime);
    const statusElement = document.getElementById(`${key}-status`);
    console.log(`Status element for ${key}:`, statusElement);
    
    if (!statusElement) {
      console.warn(`❌ Status element not found: ${key}-status`);
      continue;
    }
    
    try {
      // Only show "CHECKING..." if we don't have a status yet
      if (statusElement.textContent === '' || statusElement.textContent === '❓ UNKNOWN') {
        statusElement.textContent = '🔄 CHECKING...';
        statusElement.className = 'status-checking';
      }
      
      const response = await fetch(`${runtime.url}/health`);
      
      if (response.ok) {
        const data = await response.json();
        runtime.ready = true;
        statusElement.textContent = '✅ ONLINE';
        statusElement.className = 'status-online';
        console.log(`✅ ${runtime.name} is online:`, data);
      } else {
        throw new Error(`HTTP ${response.status}`);
      }
    } catch (error) {
      runtime.ready = false;
      statusElement.textContent = '❌ OFFLINE';
      statusElement.className = 'status-offline';
      console.warn(`❌ ${runtime.name} is offline:`, error.message);
    }
  }
  
  // Update UI based on status
  updateGameUI();
  console.log('✅ Runtime status check complete');
}

// Update game UI based on connection status
function updateGameUI() {
  const joinButton = document.getElementById('join-game');
  const leaveButton = document.getElementById('leave-game');
  const runtimeSelector = document.getElementById('runtime-selector');
  
  if (playerId) {
    // Already in game
    joinButton.disabled = true;
    leaveButton.disabled = false;
    runtimeSelector.disabled = true;
  } else {
    // Not in game
    joinButton.disabled = false;
    leaveButton.disabled = true;
    runtimeSelector.disabled = false;
  }
}

// Start Single Player Game with AI Opponents
async function startSinglePlayerGame() {
  console.log('🚀 Starting Single Player WasiCycles!');
  
  const nameInput = document.getElementById('player-name');
  const emailInput = document.getElementById('player-email');
  const tshirtSelect = document.getElementById('tshirt-size');
  playerName = nameInput.value.trim() || `Player${Math.floor(Math.random() * 1000)}`;
  playerEmail = emailInput.value.trim() || `${playerName.toLowerCase()}@wasicycles.game`;
  const tshirtSize = tshirtSelect.value || 'M';
  
  // Get selected runtime from dropdown
  const runtimeSelector = document.getElementById('runtime-selector');
  selectedRuntime = runtimeSelector.value;
  
  console.log(`🎮 Selected runtime: ${selectedRuntime}, T-shirt: ${tshirtSize}`);
  
  // Create human player ID
  playerId = `human_${playerName}_${Date.now()}`;
  window.playerId = playerId; // Store globally
  
  // Insert player into database via ORDS
  console.log('💾 Inserting player into database...');
  const dbInserted = await insertPlayer(playerId, playerName, playerEmail, selectedRuntime, tshirtSize);
  if (dbInserted) {
    console.log('✅ Player successfully added to database');
  } else {
    console.log('⚠️ Database insertion failed, continuing with game...');
  }
  
  // Reset game state
  players.clear();
  lightTrails.clear();
  aiPlayers = [];
  gameActive = true;
  
  // Create human player in 3D scene
  const startPosition = { x: -40, z: 0 }; // Back to working position
  const startDirection = 'east';
  
  createPlayer(playerId, selectedRuntime, startPosition, startDirection);
  localPlayer = players.get(playerId);
  localPlayer.isHuman = true;
  
  console.log(`✅ Created human player: ${playerId}`);
  
  // Create AI opponents
  const aiConfigs = [
    {
      id: `ai_aggressive_${Date.now()}`,
      strategy: 'aggressive',
      color: AI_STRATEGIES.aggressive.color,
      position: { x: 0, z: -40 }, // Back to working position
      direction: 'south',
      runtime: AI_STRATEGIES.aggressive.runtime
    },
    {
      id: `ai_defensive_${Date.now() + 1}`,
      strategy: 'defensive', 
      color: AI_STRATEGIES.defensive.color,
      position: { x: 40, z: 0 }, // Back to working position
      direction: 'west',
      runtime: AI_STRATEGIES.defensive.runtime
    }
  ];
  
  aiConfigs.forEach(config => {
    // Convert hex color to CSS color string for AI players
    const colorString = `#${config.color.toString(16).padStart(6, '0')}`;
    createPlayer(config.id, config.runtime, config.position, config.direction, colorString);
    const aiPlayer = players.get(config.id);
    aiPlayer.strategy = config.strategy;
    aiPlayer.isAI = true;
    aiPlayers.push(aiPlayer);
    console.log(`✅ Created AI player: ${config.id} (${config.strategy}) with color: ${colorString}`);
  });
  
  console.log(`🎮 Single player game started! Human vs ${aiPlayers.length} AI opponents`);
  updateGameStatus(`Single Player Mode: ${playerName} vs AI opponents | Use WASD/Arrows to move, C for camera`);
}

// Update game status display
function updateGameStatus(message) {
  console.log('📊 Game Status:', message);
  // Try to find a status element and update it
  const statusElement = document.getElementById('game-status') || 
                       document.getElementById('status') ||
                       document.querySelector('.status');
  if (statusElement) {
    statusElement.textContent = message;
  }
}

// Show game over overlay
function showGameOverScreen(message, isVictory = false) {
  // Remove any existing overlay
  const existingOverlay = document.getElementById('game-over-overlay');
  if (existingOverlay) {
    existingOverlay.remove();
  }
  
  // Create overlay
  const overlay = document.createElement('div');
  overlay.id = 'game-over-overlay';
  overlay.style.cssText = `
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.8);
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    z-index: 1000;
    font-family: 'Courier New', monospace;
    color: ${isVictory ? '#00ff00' : '#ff0000'};
    backdrop-filter: blur(5px);
  `;
  
  // Create message container
  const messageContainer = document.createElement('div');
  messageContainer.style.cssText = `
    text-align: center;
    background: rgba(0, 20, 40, 0.9);
    padding: 40px 60px;
    border: 2px solid ${isVictory ? '#00ff00' : '#ff0000'};
    border-radius: 10px;
    box-shadow: 0 0 30px ${isVictory ? '#00ff0050' : '#ff000050'};
  `;
  
  // Main message
  const mainMessage = document.createElement('h1');
  mainMessage.textContent = message;
  mainMessage.style.cssText = `
    font-size: 2.5em;
    margin: 0 0 20px 0;
    text-shadow: 0 0 10px ${isVictory ? '#00ff00' : '#ff0000'};
    animation: glow 1.5s ease-in-out infinite alternate;
  `;
  
  // Subtitle
  const subtitle = document.createElement('p');
  subtitle.textContent = 'Click anywhere to play again';
  subtitle.style.cssText = `
    font-size: 1.2em;
    margin: 20px 0;
    color: #00ccff;
    opacity: 0.8;
  `;
  
  // Add CSS animation
  const style = document.createElement('style');
  style.textContent = `
    @keyframes glow {
      from { text-shadow: 0 0 10px ${isVictory ? '#00ff00' : '#ff0000'}; }
      to { text-shadow: 0 0 20px ${isVictory ? '#00ff00' : '#ff0000'}, 0 0 30px ${isVictory ? '#00ff0080' : '#ff000080'}; }
    }
  `;
  document.head.appendChild(style);
  
  // Assemble the overlay
  messageContainer.appendChild(mainMessage);
  messageContainer.appendChild(subtitle);
  overlay.appendChild(messageContainer);
  document.body.appendChild(overlay);
  
  // Click to restart
  overlay.addEventListener('click', () => {
    location.reload();
  });
  
  // ESC key to restart
  document.addEventListener('keydown', function escHandler(e) {
    if (e.key === 'Escape') {
      document.removeEventListener('keydown', escHandler);
      location.reload();
    }
  });
}

// End game function
async function endGame(reason) {
  gameActive = false;
  console.log(`🏁 Game ended: ${reason}`);
  
  let message = '';
  let isVictory = false;
  
  switch(reason) {
    case 'boundary':
      message = 'GAME OVER\nYou hit the boundary wall!';
      break;
    case 'crash':
      message = 'GAME OVER\nYou crashed into a light trail!';
      break;
    case 'trail':
      message = 'GAME OVER\nYou hit a trail!';
      break;
    case 'wall':
      message = 'GAME OVER\nYou hit a wall!';
      break;
    case 'victory':
      message = 'VICTORY!\nAll AI opponents crashed!';
      isVictory = true;
      // Increment score in database for victory
      if (playerId) {
        console.log('🏆 Victory! Incrementing score in database...');
        const scoreIncremented = await incrementPlayerScore(playerId);
        if (scoreIncremented) {
          console.log('✅ Score incremented in database');
          message += '\n+1 Score Added!';
        }
      }
      break;
    default:
      message = 'GAME OVER!';
  }
  
  updateGameStatus(message.replace('\n', ' - '));
  
  // Show on-screen message instead of popup
  setTimeout(() => {
    showGameOverScreen(message, isVictory);
  }, 1000);
}

// Join the game
async function joinGame() {
  const nameInput = document.getElementById('player-name');
  const emailInput = document.getElementById('player-email');
  playerName = nameInput.value.trim() || `Player${Math.floor(Math.random() * 1000)}`;
  playerEmail = emailInput.value.trim() || '';
  
  // Get selected runtime
  const runtimeSelector = document.getElementById('runtime-selector');
  const runtimeKey = runtimeSelector.value;
  selectedRuntime = RUNTIMES[runtimeKey];
  
  if (!selectedRuntime || !selectedRuntime.ready) {
    alert('Selected runtime is not available!');
    return;
  }
  
  try {
    // Generate a unique player ID
    playerId = `${runtimeKey}_${playerName}_${Date.now()}`;
    window.playerId = playerId; // Store globally for createPlayer function
    
    console.log(`Joining WasiCycles game on ${selectedRuntime.name} with ID: ${playerId}`);
    
    // Create the local player in the 3D scene
    const startPosition = getRandomStartPosition();
    const startDirection = ['north', 'south', 'east', 'west'][Math.floor(Math.random() * 4)];
    
    createPlayer(playerId, runtimeKey, startPosition, startDirection);
    
    // Register player with the WASM runtime
    const registrationData = {
      player_id: playerId,
      name: playerName,
      game_id: gameId,
      runtime: runtimeKey,
      castle: selectedRuntime.name,
      position: startPosition,
      direction: startDirection,
      type: 'player_join'
    };
    
    // Send registration to WASM runtime 
    let response;
    if (runtimeKey === 'wasmer') {
      // Wasmer uses a different endpoint structure
      const kafkaData = {
        test_event: {
          type: 'player_join',
          player_id: playerId,
          name: playerName,
          game_id: gameId,
          runtime: runtimeKey,
          position: startPosition,
          direction: startDirection
        }
      };
      
      response = await fetch(`${selectedRuntime.url}/consume-persistent`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(kafkaData)
      });
    } else {
      // WASMEdge and Wasmtime use the /join endpoint
      const joinData = {
        player_id: playerId,
        name: playerName,
        game_id: gameId,
        runtime: runtimeKey,
        position: startPosition,
        direction: startDirection
      };
      
      response = await fetch(`${selectedRuntime.url}/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(joinData)
      });
    }
    
    if (!response.ok) {
      throw new Error(`Failed to register with ${selectedRuntime.name}: ${response.statusText}`);
    }
    
    const result = await response.json();
    console.log(`Successfully registered with ${selectedRuntime.name}:`, result);
    
    // Mark runtime as having a player
    selectedRuntime.player = playerId;
    
    // Start the game
    gameActive = true;
    
    // Update UI
    updateGameUI();
    updatePlayerStatus(playerId, 'JOINED - First Person View', selectedRuntime.color);
    
    console.log(`🎮 Joined WasiCycles! Player: ${playerId} on ${selectedRuntime.name}`);
    console.log(`🎯 Camera Mode: ${cameraMode}`);
    
  } catch (error) {
    console.error('Failed to join game:', error);
    alert(`Failed to join game: ${error.message}`);
    
    // Clean up on failure
    if (playerId) {
      removePlayer(playerId);
      playerId = null;
      window.playerId = null;
    }
    gameActive = false;
    localPlayer = null;
    cameraMode = 'overview';
  }
}

// Get a random starting position on the field
function getRandomStartPosition() {
  const margin = 10; // Keep players away from walls
  return {
    x: (Math.random() - 0.5) * (GAME_CONFIG.FIELD_SIZE - margin * 2),
    z: (Math.random() - 0.5) * (GAME_CONFIG.FIELD_SIZE - margin * 2)
  };
}

// Leave the game
async function leaveGame() {
  if (!playerId || !selectedRuntime) return;
  
  try {
    console.log(`Leaving game: ${playerId}`);
    
    // Send leave event to WASM runtime
    await fetch(`${selectedRuntime.url}/test-kafka`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        type: 'player_leave',
        player_id: playerId,
        game_id: gameId,
        runtime: Object.keys(RUNTIMES).find(key => RUNTIMES[key] === selectedRuntime)
      })
    });
    
    // Clean up local state
    removePlayer(playerId);
    selectedRuntime.player = null;
    
    playerId = null;
    selectedRuntime = null;
    gameActive = false;
    
    // Update UI
    updateGameUI();
    
    console.log('Left WasiCycles game');
  } catch (error) {
    console.error('Failed to leave game:', error);
  }
}

// Handle keyboard input for player movement
function handleKeyPress(event) {
  console.log('Key pressed:', event.key); // Debug: show all key presses
  
  // Camera toggle should work regardless of game state
  if (event.key.toLowerCase() === 'c') {
    if (localPlayer) {
      cameraMode = cameraMode === 'overview' ? 'first-person' : 'overview';
      console.log(`📷 Camera switched to: ${cameraMode}`);
    } else {
      console.log('📷 No local player - camera toggle unavailable');
    }
    return;
  }
  
  // Movement requires active game
  if (!gameActive || !playerId || !selectedRuntime) {
    console.log('Game not active, movement ignored');
    return;
  }
  
  let direction = null;
  
  switch(event.key.toLowerCase()) {
    case 'w':
    case 'arrowup':
      direction = 'north';
      break;
    case 's':
    case 'arrowdown':
      direction = 'south';
      break;
    case 'a':
    case 'arrowleft':
      direction = 'west';
      break;
    case 'd':
    case 'arrowright':
      direction = 'east';
      break;
    default:
      return; // Ignore other keys
  }
  
  if (direction) {
    sendPlayerMove(direction);
  }
}

// Send player move to WASM runtime
async function sendPlayerMove(direction) {
  const player = players.get(playerId);
  if (!player || !player.alive) return;
  
  // Calculate new position based on direction
  const newPosition = { ...player.position };
  const moveDistance = GAME_CONFIG.MOVE_SPEED;
  
  switch(direction) {
    case 'north':
      newPosition.z -= moveDistance;
      break;
    case 'south':
      newPosition.z += moveDistance;
      break;
    case 'west':
      newPosition.x -= moveDistance;
      break;
    case 'east':
      newPosition.x += moveDistance;
      break;
  }
  
  // Check boundaries
  const halfField = GAME_CONFIG.FIELD_SIZE / 2;
  newPosition.x = Math.max(-halfField + 2, Math.min(halfField - 2, newPosition.x));
  newPosition.z = Math.max(-halfField + 2, Math.min(halfField - 2, newPosition.z));
  
  try {
    // Send move to WASM runtime
    const runtimeKey = Object.keys(RUNTIMES).find(key => RUNTIMES[key] === selectedRuntime);
    await fetch(`${selectedRuntime.url}/test-kafka`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        type: 'player_move',
        player_id: playerId,
        game_id: gameId,
        runtime: runtimeKey,
        castle: selectedRuntime.name,
        position: newPosition,
        direction: direction,
        timestamp: Date.now(),
        score: 0 // Starting score
      })
    });
    
    // Update local position immediately for responsiveness
    updatePlayerPosition(playerId, newPosition, direction);
    
  } catch (error) {
    console.error('Failed to send player move:', error);
  }
}

// Update player status in UI
function updatePlayerStatus(playerId, status, color) {
  // Update player list in the UI
  const playersList = document.getElementById('players-list');
  if (playersList) {
    let playerElement = document.getElementById(`player-${playerId}`);
    if (!playerElement) {
      playerElement = document.createElement('div');
      playerElement.id = `player-${playerId}`;
      playerElement.className = 'player-item';
      playersList.appendChild(playerElement);
    }
    
    playerElement.innerHTML = `
      <span style="color: ${color}">●</span> ${playerId} - ${status}
    `;
    playerElement.style.color = color;
  }
}
