-- Ensure tables referenced by the outbound email archive foreign keys are
-- InnoDB before update-2026-07-07-outbound-email-archive.sql runs.
--
-- Fresh installs on current CARLOS database configuration already inherit
-- InnoDB. This migration is for upgraded legacy installs where one of these
-- existing tables may still be MyISAM; MyISAM tables cannot participate in
-- InnoDB foreign key constraints.
--
-- Operator diagnostic:
--   SELECT TABLE_NAME, ENGINE
--   FROM information_schema.TABLES
--   WHERE TABLE_SCHEMA = DATABASE()
--     AND TABLE_NAME IN ('document', 'emailConfig', 'emailLog');
--
-- If any row reports a non-InnoDB engine, this migration will rebuild that
-- table. Schedule the update accordingly on large legacy databases.

DROP PROCEDURE IF EXISTS assert_outbound_archive_reference_tables_exist;
DELIMITER //
CREATE PROCEDURE assert_outbound_archive_reference_tables_exist()
BEGIN
  IF EXISTS (
    SELECT 1
      FROM (
        SELECT 'document' AS table_name
        UNION ALL SELECT 'emailConfig'
        UNION ALL SELECT 'emailLog'
      ) required_reference_tables
      LEFT JOIN information_schema.TABLES actual_tables
        ON actual_tables.TABLE_SCHEMA = DATABASE()
       AND actual_tables.TABLE_NAME = required_reference_tables.table_name
       AND actual_tables.TABLE_TYPE = 'BASE TABLE'
     WHERE actual_tables.TABLE_NAME IS NULL
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Missing required outbound archive reference table';
  END IF;
END //
DELIMITER ;

CALL assert_outbound_archive_reference_tables_exist();
DROP PROCEDURE IF EXISTS assert_outbound_archive_reference_tables_exist;

DROP PROCEDURE IF EXISTS convert_table_to_innodb_if_needed;
DELIMITER //
CREATE PROCEDURE convert_table_to_innodb_if_needed(IN target_table VARCHAR(64))
BEGIN
  DECLARE current_engine VARCHAR(64);

  SELECT `ENGINE`
    INTO current_engine
    FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = target_table
     AND TABLE_TYPE = 'BASE TABLE'
   LIMIT 1;

  IF current_engine IS NULL THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Missing required outbound archive reference table';
  ELSEIF UPPER(current_engine) <> 'INNODB' THEN
    SET @convert_table_to_innodb_sql = CONCAT(
      'ALTER TABLE `',
      REPLACE(target_table, '`', '``'),
      '` ENGINE=InnoDB'
    );
    PREPARE convert_table_to_innodb_stmt FROM @convert_table_to_innodb_sql;
    EXECUTE convert_table_to_innodb_stmt;
    DEALLOCATE PREPARE convert_table_to_innodb_stmt;
  END IF;
END //
DELIMITER ;

CALL convert_table_to_innodb_if_needed('document');
CALL convert_table_to_innodb_if_needed('emailConfig');
CALL convert_table_to_innodb_if_needed('emailLog');

DROP PROCEDURE IF EXISTS convert_table_to_innodb_if_needed;
SET @convert_table_to_innodb_sql = NULL;
