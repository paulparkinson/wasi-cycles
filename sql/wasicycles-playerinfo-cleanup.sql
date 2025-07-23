-- WasiCycles Player Information Cleanup Script
-- Run this BEFORE wasicycles-playerinfo.sql to clean up existing objects
-- This ensures a clean slate for the new table structure with RUNTIME and TSHIRTSIZE fields

SET SERVEROUTPUT ON;

-- Disable ORDS object (ORDS handlers/templates/modules will be overwritten automatically)
BEGIN
  ORDS.ENABLE_OBJECT(
    p_enabled      => FALSE,
    p_schema       => 'ADMIN',
    p_object       => 'WASICYCLES_PLAYERINFO',
    p_object_type  => 'TABLE',
    p_object_alias => 'playerinfo'
  );
  DBMS_OUTPUT.PUT_LINE('Disabled ORDS object: WASICYCLES_PLAYERINFO');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('ORDS object WASICYCLES_PLAYERINFO not enabled or could not be disabled');
END;
/

-- Drop JSON Duality View
BEGIN
  EXECUTE IMMEDIATE 'DROP VIEW WASICYCLES_PLAYERINFO_DV';
  DBMS_OUTPUT.PUT_LINE('Dropped JSON Duality View: WASICYCLES_PLAYERINFO_DV');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('JSON Duality View WASICYCLES_PLAYERINFO_DV does not exist or could not be dropped');
END;
/

-- Drop indexes (Oracle will automatically drop them when table is dropped, but being explicit)
BEGIN
  EXECUTE IMMEDIATE 'DROP INDEX IDX_WASICYCLES_RUNTIME';
  DBMS_OUTPUT.PUT_LINE('Dropped index: IDX_WASICYCLES_RUNTIME');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('Index IDX_WASICYCLES_RUNTIME does not exist');
END;
/

BEGIN
  EXECUTE IMMEDIATE 'DROP INDEX IDX_WASICYCLES_GAME_TIMESTAMP';
  DBMS_OUTPUT.PUT_LINE('Dropped index: IDX_WASICYCLES_GAME_TIMESTAMP');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('Index IDX_WASICYCLES_GAME_TIMESTAMP does not exist');
END;
/

BEGIN
  EXECUTE IMMEDIATE 'DROP INDEX IDX_WASICYCLES_PLAYER_SCORE';
  DBMS_OUTPUT.PUT_LINE('Dropped index: IDX_WASICYCLES_PLAYER_SCORE');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('Index IDX_WASICYCLES_PLAYER_SCORE does not exist');
END;
/

BEGIN
  EXECUTE IMMEDIATE 'DROP INDEX IDX_WASICYCLES_PLAYER_EMAIL';
  DBMS_OUTPUT.PUT_LINE('Dropped index: IDX_WASICYCLES_PLAYER_EMAIL');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('Index IDX_WASICYCLES_PLAYER_EMAIL does not exist');
END;
/

-- Drop the main table (this will cascade drop the primary key constraint)
BEGIN
  EXECUTE IMMEDIATE 'DROP TABLE WASICYCLES_PLAYERINFO CASCADE CONSTRAINTS';
  DBMS_OUTPUT.PUT_LINE('Dropped table: WASICYCLES_PLAYERINFO with all constraints');
EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('Table WASICYCLES_PLAYERINFO does not exist or could not be dropped');
END;
/

-- Commit all changes
COMMIT;

-- Display cleanup completion message
SELECT 'WasiCycles Player Info cleanup completed successfully! You can now run wasicycles-playerinfo.sql' as STATUS FROM DUAL;
