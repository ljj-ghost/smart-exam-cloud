USE user_db;

SET @default_password_hash = '$2a$10$B6N2RKDLjePeNGCkciuBGOcDiI3p.4Rdu04.RLyo1RmjjhLxLaHqq';

-- Generate 20 teacher accounts: teacher001 ~ teacher020
INSERT INTO sys_user (id, username, password_hash, real_name, role, status)
WITH RECURSIVE teacher_seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM teacher_seq WHERE n < 20
)
SELECT
    21000 + n AS id,
    CONCAT('teacher', LPAD(n, 3, '0')) AS username,
    @default_password_hash AS password_hash,
    CONCAT('Teacher ', LPAD(n, 3, '0')) AS real_name,
    'TEACHER' AS role,
    1 AS status
FROM teacher_seq
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    real_name = VALUES(real_name),
    role = VALUES(role),
    status = VALUES(status);

-- Generate 100 student accounts: student001 ~ student100
INSERT INTO sys_user (id, username, password_hash, real_name, role, status)
WITH RECURSIVE student_seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM student_seq WHERE n < 100
)
SELECT
    31000 + n AS id,
    CONCAT('student', LPAD(n, 3, '0')) AS username,
    @default_password_hash AS password_hash,
    CONCAT('Student ', LPAD(n, 3, '0')) AS real_name,
    'STUDENT' AS role,
    1 AS status
FROM student_seq
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    real_name = VALUES(real_name),
    role = VALUES(role),
    status = VALUES(status);
