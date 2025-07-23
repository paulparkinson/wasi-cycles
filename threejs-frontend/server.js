const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 8001;

const MIME_TYPES = {
  '.html': 'text/html',
  '.css': 'text/css',
  '.js': 'application/javascript',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

const server = http.createServer((req, res) => {
  console.log(`${req.method} ${req.url}`);
  
  // Serve index.html for root path
  let filePath = req.url === '/' ? '/index.html' : req.url;
  
  // Remove query parameters for file serving
  const urlParts = filePath.split('?');
  filePath = urlParts[0];
  
  filePath = path.join(__dirname, filePath);
  
  const extname = path.extname(filePath);
  const contentType = MIME_TYPES[extname] || 'application/octet-stream';
  
    fs.readFile(filePath, (err, content) => {
    if (err) {
      if (err.code === 'ENOENT') {
        res.writeHead(404, { 'Content-Type': 'text/html' });
        res.end('<h1>404 Not Found</h1>', 'utf8');
      } else {
        res.writeHead(500);
        res.end(`Server Error: ${err.code}`, 'utf8');
      }
    } else {
      // Add cache-busting headers for development
      res.writeHead(200, { 
        'Content-Type': contentType,
        'Cache-Control': 'no-cache, no-store, must-revalidate',
        'Pragma': 'no-cache',
        'Expires': '0'
      });
      res.end(content, 'utf8');
    }
  });
});

server.listen(PORT, () => {
  console.log(`🚀 Frontend server running at http://localhost:${PORT}`);
  console.log(`🎮 WasiCycles game interface ready!`);
  console.log(`🔌 Connect to:`);
  console.log(`   - WasmEdge: http://localhost:8083`);
  console.log(`   - Wasmer:   http://localhost:8070`);
  console.log(`   - Wasmtime: http://localhost:8090`);
});
