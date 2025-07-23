#!/usr/bin/env python3
"""
WasiCycles Stateful Kafka Consumer Test Server

This version uses a persistent consumer that maintains state across HTTP requests
to test whether Wasmer/WASM environments can properly maintain consumer state.
"""

import os
import json
import time
import base64
import urllib.request
import urllib.error
from http.server import HTTPServer, SimpleHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

# Oracle Configuration
ORACLE_USERNAME = os.environ.get('ORACLE_USERNAME', 'ADMIN')
ORACLE_PASSWORD = os.environ.get('ORACLE_PASSWORD', 'mypassword')
ORACLE_DB_NAME = os.environ.get('ORACLE_DB_NAME', 'MYDATABASE')
ORACLE_HOST = os.environ.get('ORACLE_HOST', 'myhost.adb.region.oraclecloudapps.com')
ORACLE_KAFKA_REST_URL = f"https://{ORACLE_HOST}/ords/admin/_/db-api/stable/database/txeventq"
GAME_EVENTS_TOPIC = os.environ.get('KAFKA_TOPIC', 'TEST_KAFKA_TOPIC_NEW')

def safe_urlopen(request, timeout=30):
    """Wrapper for urlopen with better error handling"""
    try:
        return urllib.request.urlopen(request, timeout=timeout)
    except Exception as e:
        print(f"⚠️ Request failed: {e}")
        raise

class PersistentKafkaConsumer:
    """Persistent Kafka consumer that maintains state across HTTP requests"""
    
    def __init__(self):
        # Use topic-specific consumer group to avoid cross-contamination
        topic_safe = GAME_EVENTS_TOPIC.replace('_', '').lower()
        self.consumer_group_id = f"wasmer_{topic_safe}_consumer"
        self.instance_id = None
        self.consumed_messages = []
        self.max_stored_messages = 100
        self.consumer_initialized = False
        self.last_poll_time = 0
        self.credentials = base64.b64encode(f"{ORACLE_USERNAME}:{ORACLE_PASSWORD}".encode()).decode()
        self.object_id = f"obj_{int(time.time() * 1000)}"  # Unique object identifier
        print(f"🏗️ Created PersistentKafkaConsumer object: {self.object_id}")
        print(f"🔍 Topic: {GAME_EVENTS_TOPIC}, Consumer Group: {self.consumer_group_id}")
        
    def initialize_consumer(self):
        """Initialize the consumer group and instance (one-time setup)"""
        if self.consumer_initialized:
            print("ℹ️ Consumer already initialized")
            return True
            
        try:
            print(f"🔧 Initializing persistent consumer group: {self.consumer_group_id}")
            
            # 1. Create consumer group using cluster-based API (CRITICAL for Oracle Kafka)
            consumer_group_url = f"{ORACLE_KAFKA_REST_URL}/clusters/{ORACLE_DB_NAME}/consumer-groups/{self.consumer_group_id}"
            group_payload = json.dumps({"topic_name": GAME_EVENTS_TOPIC})
            
            print(f"🔍 Debug: Consumer group URL: {consumer_group_url}")
            print(f"🔍 Debug: Consumer group payload: {group_payload}")
            
            group_req = urllib.request.Request(consumer_group_url, data=group_payload.encode(), method='POST')
            group_req.add_header('Content-Type', 'application/json')
            group_req.add_header('Authorization', f'Basic {self.credentials}')
            
            try:
                with safe_urlopen(group_req) as response:
                    print(f"✅ Consumer group created/verified")
            except urllib.error.HTTPError as e:
                error_response = e.read().decode() if hasattr(e, 'read') else str(e)
                print(f"⚠️ Consumer group creation returned: HTTP {e.code} - {error_response}")
                if e.code == 400 and "already exists" in error_response.lower():
                    print(f"ℹ️ Consumer group already exists (expected)")
                elif e.code == 409:
                    print(f"ℹ️ Consumer group already exists (409)")
                else:
                    print(f"⚠️ Consumer group creation issue - continuing with consumer instance creation")
                    # Don't return False - continue with consumer instance creation
                    
            # 2. Create consumer instance using direct API
            consumer_instance_url = f"{ORACLE_KAFKA_REST_URL}/consumers/{self.consumer_group_id}"
            consumer_payload = json.dumps({})
            
            consumer_req = urllib.request.Request(consumer_instance_url, data=consumer_payload.encode(), method='POST')
            consumer_req.add_header('Content-Type', 'application/json')
            consumer_req.add_header('Authorization', f'Basic {self.credentials}')
            
            with safe_urlopen(consumer_req) as response:
                consumer_result = response.read().decode()
                consumer_data = json.loads(consumer_result)
                self.instance_id = consumer_data.get("instance_id", "unknown_instance")
                print(f"✅ Consumer instance created: {self.instance_id[:20]}...")
                
            # 3. Subscribe to topic
            subscription_url = f"{ORACLE_KAFKA_REST_URL}/consumers/{self.consumer_group_id}/instances/{self.instance_id}/subscription"
            subscription_payload = json.dumps({"topics": [GAME_EVENTS_TOPIC]})
            
            subscription_req = urllib.request.Request(subscription_url, data=subscription_payload.encode(), method='POST')
            subscription_req.add_header('Content-Type', 'application/json')
            subscription_req.add_header('Authorization', f'Basic {self.credentials}')
            
            try:
                with safe_urlopen(subscription_req) as response:
                    print(f"✅ Subscribed to topic: {GAME_EVENTS_TOPIC}")
            except urllib.error.HTTPError as e:
                error_response = e.read().decode() if hasattr(e, 'read') else str(e)
                print(f"⚠️ Subscription failed: HTTP {e.code} - {error_response}")
                # Continue anyway - sometimes subscription "fails" but works
                print(f"ℹ️ Continuing despite subscription error...")
                
            self.consumer_initialized = True
            print(f"🎉 Persistent consumer fully initialized!")
            return True
            
        except Exception as e:
            print(f"❌ Consumer initialization failed: {e}")
            return False
    
    def poll_messages(self):
        """Poll for new messages using the persistent consumer instance"""
        print(f"🔍 poll_messages called on object: {self.object_id}")
        
        if not self.consumer_initialized:
            print(f"🔄 Consumer not initialized, calling initialize_consumer()...")
            if not self.initialize_consumer():
                return []
        else:
            print(f"✅ Consumer already initialized, skipping initialization")
                
        try:
            current_time = time.time()
            time_since_last_poll = current_time - self.last_poll_time
            
            print(f"📡 Polling messages (time since last poll: {time_since_last_poll:.1f}s)")
            
            # Poll for records
            records_url = f"{ORACLE_KAFKA_REST_URL}/consumers/{self.consumer_group_id}/instances/{self.instance_id}/records"
            
            print(f"🔍 Debug: Polling URL: {records_url}")
            print(f"🔍 Debug: Consumer group: {self.consumer_group_id}")
            print(f"🔍 Debug: Instance ID: {self.instance_id}")
            print(f"🔍 Debug: Topic: {GAME_EVENTS_TOPIC}")
            
            records_req = urllib.request.Request(records_url, method='GET')
            records_req.add_header('Accept', 'application/json')
            records_req.add_header('Authorization', f'Basic {self.credentials}')
            
            with safe_urlopen(records_req) as response:
                records_result = response.read().decode()
                self.last_poll_time = current_time
                
                print(f"🔍 Debug: Poll response: {records_result}")
                
                if records_result.strip() and records_result != "[]":
                    print(f"✅ Found messages in response")
                    records = json.loads(records_result)
                else:
                    print(f"🔍 Debug: Empty response - no messages available or consumer offset is at end")
                    return []
                
                records = json.loads(records_result)
                new_messages = []
                
                for record in records:
                        if 'value' in record and record['value']:
                            try:
                                if isinstance(record['value'], str):
                                    value_data = json.loads(record['value'])
                                else:
                                    value_data = record['value']
                                
                                processed_record = {
                                    "topic": record.get('topic', GAME_EVENTS_TOPIC),
                                    "partition": record.get('partition', 0),
                                    "offset": record.get('offset', 'unknown'),
                                    "key": record.get('key', ''),
                                    "data": value_data,
                                    "consumed_by": "persistent_consumer",
                                    "consumed_at": int(current_time),
                                    "instance_id": self.instance_id
                                }
                                new_messages.append(processed_record)
                                
                                # Store in memory
                                self.consumed_messages.append(processed_record)
                                
                            except Exception as parse_error:
                                print(f"⚠️ Failed to parse message: {parse_error}")
                
                # Keep only recent messages
                if len(self.consumed_messages) > self.max_stored_messages:
                    self.consumed_messages = self.consumed_messages[-self.max_stored_messages:]
                
                if new_messages:
                    print(f"📨 Persistent consumer found {len(new_messages)} new messages")
                    for msg in new_messages:
                        runtime = msg.get('data', {}).get('runtime', 'unknown')
                        message_type = msg.get('data', {}).get('type', 'unknown')
                        offset = msg.get('offset', 'unknown')
                        print(f"   📧 {runtime}/{message_type} @{offset}")
                else:
                    print(f"📭 No new messages in poll")
                
                return new_messages
                    
        except Exception as e:
            print(f"⚠️ Persistent consumer poll failed: {e}")
            return []
    
    def get_status(self):
        """Get consumer status information"""
        return {
            "object_id": self.object_id,  # Track if same object is used
            "consumer_group_id": self.consumer_group_id,
            "instance_id": self.instance_id,
            "initialized": self.consumer_initialized,
            "total_consumed": len(self.consumed_messages),
            "last_poll": self.last_poll_time
        }

# Global persistent consumer instance
persistent_consumer = None

class StatefulKafkaHandler(SimpleHTTPRequestHandler):
    """HTTP handler that uses persistent Kafka consumer"""
    
    def do_OPTIONS(self):
        """Handle CORS preflight requests"""
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
    
    def do_GET(self):
        global persistent_consumer
        
        parsed_path = urlparse(self.path)
        path = parsed_path.path
        query_params = parse_qs(parsed_path.query)

        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

        print(f"🔍 Request received: {path}")

        if path.startswith('/send-message/'):
            # Extract message from path: /send-message/your-message-here
            message = path.split('/send-message/')[-1] if len(path.split('/send-message/')) > 1 else "default-test"
            
            # Create test message for Oracle TxEventQ
            test_event = {
                "type": "test_message",
                "player_id": f"test-wasmer-{message}",
                "runtime": "wasmer",
                "timestamp": int(time.time() * 1000),
                "test_data": message
            }
            
            # Publish to Oracle TxEventQ
            try:
                url = f"https://{ORACLE_HOST}/ords/admin/_/db-api/stable/database/txeventq/clusters/{ORACLE_DB_NAME}/topics/{GAME_EVENTS_TOPIC}/records"
                payload = {
                    "records": [{
                        "key": f"wasmer-{int(time.time())}",
                        "value": test_event
                    }]
                }
                
                req = urllib.request.Request(url, 
                    data=json.dumps(payload).encode(),
                    headers={
                        'Content-Type': 'application/json',
                        'Authorization': f'Basic {base64.b64encode(f"{ORACLE_USERNAME}:{ORACLE_PASSWORD}".encode()).decode()}'
                    })
                
                with safe_urlopen(req, timeout=10) as response:
                    result = response.read().decode()
                    
                data = {
                    "status": "success",
                    "message": f"Test message '{message}' sent to Oracle TxEventQ successfully",
                    "runtime": "wasmer",
                    "castle": "Wasmer Forge",
                    "test_event": test_event,
                    "kafka_result": result,
                    "timestamp": int(time.time())
                }
            except Exception as e:
                data = {
                    "status": "error",
                    "message": f"Failed to send message '{message}' to Oracle TxEventQ",
                    "runtime": "wasmer",
                    "castle": "Wasmer Forge",  
                    "error": str(e),
                    "timestamp": int(time.time())
                }
            
        elif path == '/health':
            data = {
                "status": "healthy",
                "runtime": "wasmer" if os.environ.get('PORT') == '8070' else "python_native",
                "castle": "Wasmer Cycle",
                "service": "WasiCycles Wasmer Cycle",
                "timestamp": int(time.time()),
                "version": "1.0.0",
                "consumer_status": persistent_consumer.get_status() if persistent_consumer else "not_initialized"
            }
            
        elif path == '/consume-persistent':
            # Use persistent consumer to poll messages
            if not persistent_consumer:
                data = {
                    "status": "error",
                    "error": "Persistent consumer not initialized"
                }
            else:
                messages = persistent_consumer.poll_messages()
                data = {
                    "status": "success",
                    "runtime": "python_native" if not os.environ.get('WASMER_RUNTIME') else "wasmer",
                    "endpoint": "consume_persistent",
                    "messages": messages,
                    "message_count": len(messages),
                    "consumer_status": persistent_consumer.get_status(),
                    "note": "Using persistent consumer - maintains state across requests",
                    "timestamp": int(time.time())
                }
        
        elif path == '/consumer-status':
            # Get detailed consumer status
            if not persistent_consumer:
                data = {
                    "status": "error",
                    "error": "Persistent consumer not initialized"
                }
            else:
                status = persistent_consumer.get_status()
                data = {
                    "status": "success",
                    "consumer_status": status,
                    "all_consumed_messages": persistent_consumer.consumed_messages[-10:],  # Last 10 messages
                    "timestamp": int(time.time())
                }
                
        elif path == '/consume-direct-plsql':
            # Consume messages directly via PLSQL procedure exposed as ORDS REST endpoint
            try:
                # Call our PLSQL wasm_consume_json procedure directly via ORDS
                # Using the new ORDS REST module we just created
                plsql_url = f"https://{ORACLE_HOST}/ords/admin/wasm-kafka/consume-direct"
                
                # Create request with topic parameter
                payload = json.dumps({
                    "topic_name": GAME_EVENTS_TOPIC,
                    "consumer_name": "wasmer_direct_consumer",
                    "timeout": 2
                })
                
                req = urllib.request.Request(plsql_url, data=payload.encode(), method='POST')
                req.add_header('Content-Type', 'application/json')
                req.add_header('Accept', 'application/json')
                req.add_header('Authorization', f'Basic {self.credentials}' if hasattr(self, 'credentials') else f'Basic {base64.b64encode(f"{ORACLE_USERNAME}:{ORACLE_PASSWORD}".encode()).decode()}')
                
                with urllib.request.urlopen(req, timeout=10) as response:
                    plsql_result = response.read().decode()
                    
                    # ORDS returns data in different format: {"items":[{"result": "..."}]}
                    ords_response = json.loads(plsql_result)
                    
                    if "items" in ords_response and len(ords_response["items"]) > 0:
                        # Extract the result from ORDS wrapper
                        result_json = ords_response["items"][0].get("result", "{}")
                        plsql_data = json.loads(result_json) if isinstance(result_json, str) else result_json
                    else:
                        # Fallback to direct parsing
                        plsql_data = ords_response
                    
                    # The response is already in the format we want from wasm_consume_json function
                    data = {
                        "status": plsql_data.get("status", "success"),
                        "runtime": "wasmer_direct",
                        "endpoint": "consume_direct_plsql", 
                        "messages": plsql_data.get("messages", []),
                        "message_count": plsql_data.get("count", 0),
                        "plsql_response": plsql_data,
                        "ords_response": ords_response,  # Include raw ORDS response for debugging
                        "consumer_name": plsql_data.get("consumer_name", "unknown"),
                        "timeout_seconds": plsql_data.get("timeout_seconds", 2),
                        "note": "Direct WASM to database via ORDS REST + PLSQL procedure",
                        "ords_url": plsql_url,
                        "timestamp": int(time.time())
                    }
                    
            except Exception as e:
                data = {
                    "status": "error",
                    "error": f"Failed to consume via direct PLSQL: {str(e)}",
                    "runtime": "wasmer_direct",
                    "endpoint": "consume_direct_plsql",
                    "messages": [],
                    "message_count": 0,
                    "ords_url": f"https://{ORACLE_HOST}/ords/admin/wasm-kafka/consume-direct",
                    "timestamp": int(time.time())
                }
                
        elif path == '/initialize-consumer':
            # Force re-initialization of consumer
            if persistent_consumer:
                success = persistent_consumer.initialize_consumer()
                data = {
                    "status": "success" if success else "error",
                    "initialized": success,
                    "consumer_status": persistent_consumer.get_status(),
                    "timestamp": int(time.time())
                }
            else:
                data = {
                    "status": "error",
                    "error": "Persistent consumer object not created"
                }
        
        else:
            data = {
                "message": "🧪 WasiCycles Stateful Kafka Test Server",
                "runtime": "python_native" if not os.environ.get('WASMER_RUNTIME') else "wasmer",
                "endpoints": {
                    "health": "/health",
                    "consume_persistent": "/consume-persistent",
                    "consume_direct_plsql": "/consume-direct-plsql",
                    "consumer_status": "/consumer-status",
                    "initialize_consumer": "/initialize-consumer"
                },
                "description": "Tests persistent Kafka consumer state across HTTP requests"
            }

        response = json.dumps(data, indent=2)
        self.wfile.write(response.encode())

    def do_POST(self):
        # Simple test message publisher (reuse from main.py logic)
        parsed_path = urlparse(self.path)
        path = parsed_path.path

        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

        if path == '/test-kafka':
            # Publish a test message
            try:
                content_length = int(self.headers['Content-Length'])
                post_data = self.rfile.read(content_length)
                request_data = json.loads(post_data.decode())
                
                test_message = request_data.get('test_message', 'stateful_test')
                
                # Create test event
                test_event = {
                    "type": "test_message",
                    "player_id": f"test-stateful-{test_message}",
                    "runtime": "wasmer" if os.environ.get('PORT') == '8070' else "python_native",
                    "timestamp": int(time.time()),
                    "test_data": test_message
                }
                
                # Publish to Oracle Kafka
                credentials = base64.b64encode(f"{ORACLE_USERNAME}:{ORACLE_PASSWORD}".encode()).decode()
                
                # Use the working URL format (without /messages suffix)
                publish_url = f"{ORACLE_KAFKA_REST_URL}/topics/{GAME_EVENTS_TOPIC}"
                # Use the working payload format with "records"
                publish_payload = json.dumps({
                    "records": [{
                        "key": f"stateful-{int(time.time())}",
                        "value": json.dumps(test_event)
                    }]
                })
                
                publish_req = urllib.request.Request(publish_url, data=publish_payload.encode(), method='POST')
                publish_req.add_header('Content-Type', 'application/json')
                publish_req.add_header('Accept', 'application/json')
                publish_req.add_header('Authorization', f'Basic {credentials}')
                
                with safe_urlopen(publish_req) as response:
                    publish_result = response.read().decode()
                    
                data = {
                    "status": "success",
                    "test_event": test_event,
                    "published_to": GAME_EVENTS_TOPIC,
                    "timestamp": int(time.time())
                }
                
            except Exception as e:
                data = {
                    "status": "error",
                    "error": str(e),
                    "timestamp": int(time.time())
                }
                
        elif path == '/test-plsql-enqueue':
            # Publish via PLSQL enqueue (should be compatible with PLSQL dequeue)
            try:
                content_length = int(self.headers['Content-Length'])
                post_data = self.rfile.read(content_length)
                request_data = json.loads(post_data.decode())
                
                test_message = request_data.get('test_message', 'plsql_enqueue_test')
                
                # Create test event
                test_event = {
                    "type": "plsql_enqueue_test",
                    "player_id": f"test-plsql-{test_message}",
                    "runtime": "wasmer_plsql_direct",
                    "timestamp": int(time.time()),
                    "test_data": test_message,
                    "method": "plsql_enqueue"
                }
                
                # Publish via ORDS PLSQL enqueue endpoint
                credentials = base64.b64encode(f"{ORACLE_USERNAME}:{ORACLE_PASSWORD}".encode()).decode()
                plsql_publish_url = f"https://{ORACLE_HOST}/ords/admin/wasm-kafka/publish"
                
                plsql_payload = json.dumps({
                    "topic_name": GAME_EVENTS_TOPIC,
                    "message": json.dumps(test_event),
                    "key": f"plsql-{int(time.time())}"
                })
                
                plsql_req = urllib.request.Request(plsql_publish_url, data=plsql_payload.encode(), method='POST')
                plsql_req.add_header('Content-Type', 'application/json')
                plsql_req.add_header('Accept', 'application/json')
                plsql_req.add_header('Authorization', f'Basic {credentials}')
                
                with safe_urlopen(plsql_req) as response:
                    plsql_result = response.read().decode()
                    plsql_data = json.loads(plsql_result)
                    
                data = {
                    "status": plsql_data.get("status", "unknown"),
                    "test_event": test_event,
                    "published_to": GAME_EVENTS_TOPIC,
                    "method": "plsql_enqueue",
                    "plsql_response": plsql_data,
                    "timestamp": int(time.time())
                }
                
            except Exception as e:
                data = {
                    "status": "error",
                    "error": f"PLSQL enqueue failed: {str(e)}",
                    "method": "plsql_enqueue",
                    "timestamp": int(time.time())
                }
                
        else:
            data = {
                "status": "error",
                "error": f"Unknown POST endpoint: {path}"
            }

        response = json.dumps(data, indent=2)
        self.wfile.write(response.encode())

def run_server():
    """Run the stateful Kafka test server"""
    global persistent_consumer
    
    host = os.environ.get('HOST', '127.0.0.1')
    port = int(os.environ.get('PORT', 8070))  # Use 8070 to match run.sh
    
    print(f"🧪 Starting Stateful Kafka Test Server on {host}:{port}")
    print("🎯 Testing persistent consumer state across HTTP requests")
    print("📊 Endpoints: /health, /consume-persistent, /consumer-status, /initialize-consumer")
    print("🔬 This will test if Wasmer can maintain Kafka consumer state")
    
    # Initialize persistent consumer
    persistent_consumer = PersistentKafkaConsumer()
    print("✅ Created persistent consumer instance")
    
    server = HTTPServer((host, port), StatefulKafkaHandler)
    try:
        print(f"🚀 Server ready! Test with:")
        print(f"   curl http://{host}:{port}/health")
        print(f"   curl http://{host}:{port}/consume-persistent")
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n🛑 Shutting down stateful test server...")
        server.server_close()

if __name__ == '__main__':
    run_server()
