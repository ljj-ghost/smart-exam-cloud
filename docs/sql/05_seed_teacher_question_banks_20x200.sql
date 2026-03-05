-- Seed independent question banks for 20 teachers.
-- Scope: teacher001 ~ teacher020, each 200 questions, total 4000 questions.
-- Types per teacher: SINGLE 40, MULTI 40, JUDGE 40, FILL 40, SHORT 40.
-- Safe to rerun: deterministic question IDs + ON DUPLICATE KEY UPDATE.

USE question_db;

INSERT INTO q_question (
    id,
    type,
    stem,
    difficulty,
    knowledge_point,
    analysis,
    answer,
    options_json,
    created_by
)
WITH RECURSIVE question_seq AS (
    SELECT 1 AS question_no
    UNION ALL
    SELECT question_no + 1
    FROM question_seq
    WHERE question_no < 200
),
teacher_accounts AS (
    SELECT
        id AS user_id,
        CAST(SUBSTRING(username, 8, 3) AS UNSIGNED) AS teacher_no
    FROM user_db.sys_user
    WHERE role = 'TEACHER'
      AND status = 1
      AND username REGEXP '^teacher[0-9]{3}$'
      AND CAST(SUBSTRING(username, 8, 3) AS UNSIGNED) BETWEEN 1 AND 20
)
SELECT
    x.question_id AS id,
    x.question_type AS type,
    CASE
        WHEN x.question_type = 'SINGLE' THEN CONCAT(
            '[T', LPAD(x.teacher_no, 3, '0'), '-S-', LPAD(x.question_no, 3, '0'),
            '] ', x.topic_name, ': choose the most accurate statement.'
        )
        WHEN x.question_type = 'MULTI' THEN CONCAT(
            '[T', LPAD(x.teacher_no, 3, '0'), '-M-', LPAD(x.question_no, 3, '0'),
            '] ', x.topic_name, ': choose all correct statements.'
        )
        WHEN x.question_type = 'JUDGE' THEN CONCAT(
            '[T', LPAD(x.teacher_no, 3, '0'), '-J-', LPAD(x.question_no, 3, '0'),
            '] ', x.topic_name, ': determine whether the statement is true or false.'
        )
        WHEN x.question_type = 'FILL' THEN CONCAT(
            '[T', LPAD(x.teacher_no, 3, '0'), '-F-', LPAD(x.question_no, 3, '0'),
            '] ', x.topic_name, ': fill in the key term.'
        )
        ELSE CONCAT(
            '[T', LPAD(x.teacher_no, 3, '0'), '-H-', LPAD(x.question_no, 3, '0'),
            '] ', x.topic_name, ': explain core principles, practical usage, and common risks.'
        )
    END AS stem,
    x.difficulty,
    x.topic_name AS knowledge_point,
    CASE
        WHEN x.question_type = 'SINGLE' THEN CONCAT('Single-choice rationale: verify concept boundary in ', x.topic_name, '.')
        WHEN x.question_type = 'MULTI' THEN CONCAT('Multi-choice rationale: check multiple correct viewpoints in ', x.topic_name, '.')
        WHEN x.question_type = 'JUDGE' THEN CONCAT('Judge rationale: validate key conclusion in ', x.topic_name, '.')
        WHEN x.question_type = 'FILL' THEN CONCAT('Fill rationale: answer should be the high-frequency key term in ', x.topic_name, '.')
        ELSE CONCAT('Short-answer scoring points: concept accuracy, complete logic, and practical context in ', x.topic_name, '.')
    END AS analysis,
    CASE
        WHEN x.question_type = 'SINGLE' THEN
            CASE MOD(x.question_no + x.teacher_no, 4)
                WHEN 0 THEN 'A'
                WHEN 1 THEN 'B'
                WHEN 2 THEN 'C'
                ELSE 'D'
            END
        WHEN x.question_type = 'MULTI' THEN
            CASE MOD(x.question_no + x.teacher_no, 6)
                WHEN 0 THEN 'A,B'
                WHEN 1 THEN 'A,C'
                WHEN 2 THEN 'B,C'
                WHEN 3 THEN 'B,D'
                WHEN 4 THEN 'A,D'
                ELSE 'C,D'
            END
        WHEN x.question_type = 'JUDGE' THEN
            CASE MOD(x.question_no + x.teacher_no, 2)
                WHEN 0 THEN 'true'
                ELSE 'false'
            END
        WHEN x.question_type = 'FILL' THEN
            CASE x.topic_name
                WHEN 'Java' THEN 'JVM'
                WHEN 'Python' THEN 'PEP8'
                WHEN 'Computer Network' THEN 'TCP'
                WHEN 'Operating System' THEN 'process'
                WHEN 'Database' THEN 'transaction'
                WHEN 'Data Structure' THEN 'time complexity'
                WHEN 'Algorithm' THEN 'dynamic programming'
                WHEN 'Linux' THEN 'shell'
                WHEN 'Security' THEN 'least privilege'
                ELSE 'single responsibility principle'
            END
        ELSE CONCAT(
            'Expected points: define key concepts in ', x.topic_name,
            ', describe implementation path, and provide practical tradeoff examples.'
        )
    END AS answer,
    CASE
        WHEN x.question_type IN ('SINGLE', 'MULTI') THEN JSON_ARRAY(
            JSON_OBJECT('key', 'A', 'text', CONCAT(x.topic_name, ' statement A')),
            JSON_OBJECT('key', 'B', 'text', CONCAT(x.topic_name, ' statement B')),
            JSON_OBJECT('key', 'C', 'text', CONCAT(x.topic_name, ' statement C')),
            JSON_OBJECT('key', 'D', 'text', CONCAT(x.topic_name, ' statement D'))
        )
        ELSE JSON_ARRAY()
    END AS options_json,
    x.created_by
FROM (
    SELECT
        760000000000 + (t.teacher_no * 1000) + q.question_no AS question_id,
        t.user_id AS created_by,
        t.teacher_no,
        q.question_no,
        CASE
            WHEN q.question_no <= 40 THEN 'SINGLE'
            WHEN q.question_no <= 80 THEN 'MULTI'
            WHEN q.question_no <= 120 THEN 'JUDGE'
            WHEN q.question_no <= 160 THEN 'FILL'
            ELSE 'SHORT'
        END AS question_type,
        CASE MOD(q.question_no + t.teacher_no - 1, 10)
            WHEN 0 THEN 'Java'
            WHEN 1 THEN 'Python'
            WHEN 2 THEN 'Computer Network'
            WHEN 3 THEN 'Operating System'
            WHEN 4 THEN 'Database'
            WHEN 5 THEN 'Data Structure'
            WHEN 6 THEN 'Algorithm'
            WHEN 7 THEN 'Linux'
            WHEN 8 THEN 'Security'
            ELSE 'Software Engineering'
        END AS topic_name,
        1 + MOD(q.question_no + t.teacher_no - 1, 5) AS difficulty
    FROM teacher_accounts t
    CROSS JOIN question_seq q
) x
ON DUPLICATE KEY UPDATE
    type = VALUES(type),
    stem = VALUES(stem),
    difficulty = VALUES(difficulty),
    knowledge_point = VALUES(knowledge_point),
    analysis = VALUES(analysis),
    answer = VALUES(answer),
    options_json = VALUES(options_json),
    created_by = VALUES(created_by);

