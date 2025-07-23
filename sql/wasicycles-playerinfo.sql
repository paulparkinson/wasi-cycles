-- WasiCycles Player Information Table and JSON Duality View
-- Created for cross-runtime multiplayer game leaderboard integration

-- Create the player information table
CREATE TABLE WASICYCLES_PLAYERINFO (
    PLAYER_ID VARCHAR2(50) NOT NULL,
    PLAYER_NAME VARCHAR2(100) NOT NULL,
    PLAYER_EMAIL VARCHAR2(255) NOT NULL,
    GAME_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PLAYER_SCORE NUMBER(10) DEFAULT 0,
    RUNTIME VARCHAR2(50),
    TSHIRTSIZE VARCHAR2(10),
    CONSTRAINT PK_WASICYCLES_PLAYERINFO PRIMARY KEY (PLAYER_ID)
);

-- Create indexes for performance
CREATE INDEX IDX_WASICYCLES_PLAYER_EMAIL ON WASICYCLES_PLAYERINFO(PLAYER_EMAIL);
CREATE INDEX IDX_WASICYCLES_PLAYER_SCORE ON WASICYCLES_PLAYERINFO(PLAYER_SCORE DESC);
CREATE INDEX IDX_WASICYCLES_GAME_TIMESTAMP ON WASICYCLES_PLAYERINFO(GAME_TIMESTAMP DESC);
CREATE INDEX IDX_WASICYCLES_RUNTIME ON WASICYCLES_PLAYERINFO(RUNTIME);

-- Drop the existing duality view if it exists (fix for duplicate column mapping)
BEGIN
  EXECUTE IMMEDIATE 'DROP VIEW WASICYCLES_PLAYERINFO_DV';
  DBMS_OUTPUT.PUT_LINE('Dropped existing WASICYCLES_PLAYERINFO_DV');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('View WASICYCLES_PLAYERINFO_DV does not exist or could not be dropped');
END;
/

-- Create JSON Relational Duality View for MongoDB API access
-- Note: Each database column can only be mapped to ONE JSON field (no duplicate mappings)
CREATE JSON RELATIONAL DUALITY VIEW WASICYCLES_PLAYERINFO_DV AS
  SELECT JSON {
    '_id'              : PLAYER_ID,
    'playerName'       : PLAYER_NAME,
    'playerEmail'      : PLAYER_EMAIL,
    'gameTimestamp'    : GAME_TIMESTAMP,
    'playerScore'      : PLAYER_SCORE,
    'runtime'          : RUNTIME,
    'tshirtSize'       : TSHIRTSIZE
  }
  FROM WASICYCLES_PLAYERINFO
  WITH INSERT UPDATE DELETE CHECK;

-- Insert sample data for testing
INSERT INTO WASICYCLES_PLAYERINFO (PLAYER_ID, PLAYER_NAME, PLAYER_EMAIL, PLAYER_SCORE, RUNTIME, TSHIRTSIZE) 
VALUES ('test-player-1', 'Test Player 1', 'test1@example.com', 150, 'wasmer', 'L');

INSERT INTO WASICYCLES_PLAYERINFO (PLAYER_ID, PLAYER_NAME, PLAYER_EMAIL, PLAYER_SCORE, RUNTIME, TSHIRTSIZE) 
VALUES ('test-player-2', 'Test Player 2', 'test2@example.com', 300, 'wasmedge', 'M');

INSERT INTO WASICYCLES_PLAYERINFO (PLAYER_ID, PLAYER_NAME, PLAYER_EMAIL, PLAYER_SCORE, RUNTIME, TSHIRTSIZE) 
VALUES ('test-player-3', 'Test Player 3', 'test3@example.com', 225, 'wasmtime', 'XL');

-- Commit the changes
COMMIT;

-- Enable ORDS for the table
BEGIN
  ORDS.ENABLE_SCHEMA(
    p_enabled             => TRUE,
    p_schema              => 'ADMIN',
    p_url_mapping_type    => 'BASE_PATH',
    p_url_mapping_pattern => 'admin',
    p_auto_rest_auth      => FALSE
  );
END;
/

-- Enable REST access for the WASICYCLES_PLAYERINFO table
BEGIN
  ORDS.ENABLE_OBJECT(
    p_enabled      => TRUE,
    p_schema       => 'ADMIN',
    p_object       => 'WASICYCLES_PLAYERINFO',
    p_object_type  => 'TABLE',
    p_object_alias => 'playerinfo',
    p_auto_rest_auth => FALSE
  );
END;
/

-- Create custom ORDS endpoints for leaderboard functionality
BEGIN
  ORDS.DEFINE_MODULE(
    p_module_name    => 'wasicycles',
    p_base_path      => '/wasicycles/',
    p_items_per_page => 25
  );
END;
/

-- Leaderboard endpoint - top players by score
BEGIN
  ORDS.DEFINE_HANDLER(
    p_module_name    => 'wasicycles',
    p_pattern        => 'leaderboard',
    p_method         => 'GET',
    p_source_type    => ORDS.source_type_query,
    p_source         => 'SELECT PLAYER_ID, PLAYER_NAME, PLAYER_EMAIL, PLAYER_SCORE, GAME_TIMESTAMP, RUNTIME, TSHIRTSIZE
                         FROM WASICYCLES_PLAYERINFO 
                         ORDER BY PLAYER_SCORE DESC, GAME_TIMESTAMP DESC 
                         FETCH FIRST 10 ROWS ONLY'
  );
END;
/

-- Player update endpoint (for inserting new players and updating scores)
BEGIN
  ORDS.DEFINE_TEMPLATE(
    p_module_name    => 'wasicycles',
    p_pattern        => 'player/:player_id'
  );
END;
/

BEGIN
  ORDS.DEFINE_HANDLER(
    p_module_name    => 'wasicycles',
    p_pattern        => 'player/:player_id',
    p_method         => 'PUT',
    p_source_type    => ORDS.source_type_plsql,
    p_source         => '
    BEGIN
      UPDATE WASICYCLES_PLAYERINFO 
      SET PLAYER_NAME = :player_name,
          PLAYER_EMAIL = :player_email,
          PLAYER_SCORE = :player_score,
          RUNTIME = :runtime,
          TSHIRTSIZE = :tshirtsize,
          GAME_TIMESTAMP = CURRENT_TIMESTAMP
      WHERE PLAYER_ID = :player_id;
      
      IF SQL%ROWCOUNT = 0 THEN
        INSERT INTO WASICYCLES_PLAYERINFO (PLAYER_ID, PLAYER_NAME, PLAYER_EMAIL, PLAYER_SCORE, RUNTIME, TSHIRTSIZE)
        VALUES (:player_id, :player_name, :player_email, :player_score, :runtime, :tshirtsize);
      END IF;
      
      :status := 200;
    EXCEPTION
      WHEN OTHERS THEN
        :status := 500;
        :message := SQLERRM;
    END;'
  );
END;
/

-- Player increment score endpoint (for victories)
BEGIN
  ORDS.DEFINE_TEMPLATE(
    p_module_name    => 'wasicycles',
    p_pattern        => 'player/:player_id/increment-score'
  );
END;
/

BEGIN
  ORDS.DEFINE_HANDLER(
    p_module_name    => 'wasicycles',
    p_pattern        => 'player/:player_id/increment-score',
    p_method         => 'POST',
    p_source_type    => ORDS.source_type_plsql,
    p_source         => '
    BEGIN
      UPDATE WASICYCLES_PLAYERINFO 
      SET PLAYER_SCORE = PLAYER_SCORE + 1,
          GAME_TIMESTAMP = CURRENT_TIMESTAMP
      WHERE PLAYER_ID = :player_id;
      
      IF SQL%ROWCOUNT = 0 THEN
        :status := 404;
        :message := ''Player not found'';
      ELSE
        :status := 200;
        :message := ''Score incremented successfully'';
      END IF;
    EXCEPTION
      WHEN OTHERS THEN
        :status := 500;
        :message := SQLERRM;
    END;'
  );
END;
/

-- Commit all ORDS configuration
COMMIT;

-- Test the duality view
SELECT * FROM WASICYCLES_PLAYERINFO_DV WHERE ROWNUM <= 3;

-- Test direct query on base table
SELECT PLAYER_ID, PLAYER_NAME, PLAYER_EMAIL, GAME_TIMESTAMP, PLAYER_SCORE, RUNTIME, TSHIRTSIZE
FROM WASICYCLES_PLAYERINFO 
WHERE ROWNUM <= 3;

-- Display setup completion message
SELECT 'WasiCycles Player Info table with RUNTIME and TSHIRTSIZE fields, fixed JSON Duality View, and ORDS endpoints created successfully!' as STATUS FROM DUAL;
