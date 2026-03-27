-- Migrate GOVERNANCE role type to DATA (GOVERNANCE removed from application)
UPDATE roles SET role_type = 'DATA' WHERE role_type = 'GOVERNANCE';
UPDATE permissions SET permission_type = 'DATA' WHERE permission_type = 'GOVERNANCE';
