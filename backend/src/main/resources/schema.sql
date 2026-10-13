-- ============================================================
-- 线上题库与在线考试刷题系统 - MySQL 8.0 数据库初始化脚本
-- 建议字符集：utf8mb4 / 排序规则：utf8mb4_0900_ai_ci
--
-- 本脚本是「只增不删」的幂等初始化脚本，可对已有数据的库安全地重复执行：
--   * 全部建表语句使用 CREATE TABLE IF NOT EXISTS，不重建、不清空任何表；
--   * 角色/权限/角色权限种子使用 INSERT IGNORE；
--   * 学科/知识点示例数据带 NOT EXISTS 守卫，不会产生重复行。
-- 表结构演进请使用迁移工具（Flyway/Liquibase），不要直接改本文件后对生产库执行。
-- ============================================================

CREATE DATABASE IF NOT EXISTS online_exam
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE online_exam;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 1. 用户与权限
-- ------------------------------------------------------------
-- 本脚本刻意不使用 DROP TABLE：对已有数据的库重复执行时不得清空任何表。
-- 表结构重建请交给迁移工具（Flyway/Liquibase），不要依赖初始化脚本。
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '登录用户名',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt密码哈希',
    nickname VARCHAR(50) NOT NULL COMMENT '昵称',
    avatar VARCHAR(500) NULL COMMENT '头像URL',
    email VARCHAR(100) NULL COMMENT '邮箱',
    phone VARCHAR(20) NULL COMMENT '手机号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    last_login_at DATETIME NULL COMMENT '最后登录时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_user_username (username),
    UNIQUE KEY uk_sys_user_email (email),
    UNIQUE KEY uk_sys_user_phone (phone),
    KEY idx_sys_user_status (status),
    KEY idx_sys_user_created_at (created_at)
) ENGINE=InnoDB COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    description VARCHAR(255) NULL COMMENT '角色描述',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB COMMENT='系统角色';

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '权限ID',
    permission_code VARCHAR(100) NOT NULL COMMENT '权限编码',
    permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    resource VARCHAR(100) NULL COMMENT '资源名称',
    action VARCHAR(50) NULL COMMENT '操作类型',
    description VARCHAR(255) NULL COMMENT '权限描述',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_permission_code (permission_code)
) ENGINE=InnoDB COMMENT='系统权限';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_user_role (user_id, role_id),
    KEY idx_sys_user_role_role (role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB COMMENT='用户角色关系';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_role_permission (role_id, permission_id),
    KEY idx_sys_role_permission_permission (permission_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB COMMENT='角色权限关系';

-- ------------------------------------------------------------
-- 2. 学科与知识点
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subject (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '学科ID',
    name VARCHAR(100) NOT NULL COMMENT '学科名称',
    description VARCHAR(500) NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    sort INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_subject_name (name),
    KEY idx_subject_status_sort (status, sort)
) ENGINE=InnoDB COMMENT='学科';

CREATE TABLE IF NOT EXISTS knowledge_point (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识点ID',
    subject_id BIGINT NOT NULL,
    parent_id BIGINT NULL COMMENT '父知识点，NULL表示一级节点',
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    sort INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_kp_subject_parent (subject_id, parent_id),
    KEY idx_kp_status_sort (status, sort),
    CONSTRAINT fk_kp_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_kp_parent FOREIGN KEY (parent_id) REFERENCES knowledge_point(id)
) ENGINE=InnoDB COMMENT='知识点';

-- ------------------------------------------------------------
-- 3. 题库
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '题目ID',
    subject_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL COMMENT '题型编码',
    content LONGTEXT NOT NULL COMMENT '题干',
    answer_config JSON NULL COMMENT '非选项型答案配置，如填空/编程判题配置',
    analysis LONGTEXT NULL COMMENT '解析',
    difficulty VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' COMMENT 'EASY/MEDIUM/HARD',
    difficulty_value DECIMAL(4,2) NOT NULL DEFAULT 0.50 COMMENT '0~1难度参数',
    default_score DECIMAL(8,2) NOT NULL DEFAULT 1.00 COMMENT '默认分值',
    tags JSON NULL COMMENT '标签数组',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    creator_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_question_subject_type (subject_id, type),
    KEY idx_question_kp_type_diff (knowledge_point_id, type, difficulty),
    KEY idx_question_status (status),
    KEY idx_question_creator (creator_id),
    CONSTRAINT fk_question_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_question_kp FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id),
    CONSTRAINT fk_question_creator FOREIGN KEY (creator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB COMMENT='题目';

CREATE TABLE IF NOT EXISTS question_option (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '选项ID',
    question_id BIGINT NOT NULL,
    option_key VARCHAR(10) NOT NULL COMMENT 'A/B/C/D...',
    option_content TEXT NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    is_correct TINYINT NOT NULL DEFAULT 0 COMMENT '1正确 0错误',
    UNIQUE KEY uk_question_option_key (question_id, option_key),
    KEY idx_question_option_question_sort (question_id, sort),
    CONSTRAINT fk_question_option_question FOREIGN KEY (question_id) REFERENCES question(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='题目选项';

-- ------------------------------------------------------------
-- 4. 试卷与试卷快照
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS paper (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '试卷ID',
    name VARCHAR(200) NOT NULL COMMENT '试卷名称',
    subject_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL COMMENT 'FIXED/RULE',
    total_score DECIMAL(8,2) NOT NULL DEFAULT 0,
    duration INT NOT NULL COMMENT '考试时长，分钟',
    rule_config JSON NULL COMMENT '动态组卷规则',
    creator_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_paper_subject_status (subject_id, status),
    KEY idx_paper_creator (creator_id),
    CONSTRAINT fk_paper_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_paper_creator FOREIGN KEY (creator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB COMMENT='试卷';

CREATE TABLE IF NOT EXISTS paper_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '试卷题目ID',
    paper_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL COMMENT '来源题库题目ID',
    question_no INT NOT NULL COMMENT '题号',
    score DECIMAL(8,2) NOT NULL COMMENT '该题分值',
    sort INT NOT NULL COMMENT '排序',
    content_snapshot LONGTEXT NOT NULL COMMENT '题干快照',
    type_snapshot VARCHAR(30) NOT NULL COMMENT '题型快照',
    answer_config_snapshot JSON NULL COMMENT '答案配置快照',
    analysis_snapshot LONGTEXT NULL COMMENT '解析快照',
    difficulty_snapshot VARCHAR(20) NULL COMMENT '难度快照',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_paper_question_no (paper_id, question_no),
    UNIQUE KEY uk_paper_question_pair (paper_id, question_id),
    KEY idx_paper_question_paper_sort (paper_id, sort),
    CONSTRAINT fk_paper_question_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE CASCADE,
    CONSTRAINT fk_paper_question_source FOREIGN KEY (question_id) REFERENCES question(id)
) ENGINE=InnoDB COMMENT='试卷题目及题目快照';

CREATE TABLE IF NOT EXISTS paper_question_option (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_question_id BIGINT NOT NULL,
    option_key VARCHAR(10) NOT NULL,
    option_content TEXT NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    is_correct TINYINT NOT NULL DEFAULT 0 COMMENT '服务端内部使用；对考生接口不得直接返回',
    UNIQUE KEY uk_paper_question_option_key (paper_question_id, option_key),
    KEY idx_paper_question_option_sort (paper_question_id, sort),
    CONSTRAINT fk_paper_question_option_pq FOREIGN KEY (paper_question_id) REFERENCES paper_question(id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='试卷选项快照';

-- ------------------------------------------------------------
-- 5. 考试
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '考试ID',
    name VARCHAR(200) NOT NULL,
    paper_id BIGINT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    duration INT NOT NULL COMMENT '时长，分钟',
    max_attempts INT NOT NULL DEFAULT 1,
    candidate_scope VARCHAR(20) NOT NULL DEFAULT 'ALL' COMMENT 'ALL/ASSIGNED',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ENDED/ARCHIVED',
    creator_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CHECK (end_time > start_time),
    CHECK (duration > 0),
    CHECK (max_attempts > 0),
    KEY idx_exam_time_status (status, start_time, end_time),
    KEY idx_exam_creator (creator_id),
    CONSTRAINT fk_exam_paper FOREIGN KEY (paper_id) REFERENCES paper(id),
    CONSTRAINT fk_exam_creator FOREIGN KEY (creator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB COMMENT='考试';

CREATE TABLE IF NOT EXISTS exam_candidate (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exam_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exam_candidate (exam_id, user_id),
    KEY idx_exam_candidate_user (user_id),
    CONSTRAINT fk_exam_candidate_exam FOREIGN KEY (exam_id) REFERENCES exam(id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_candidate_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB COMMENT='考试指定考生';

CREATE TABLE IF NOT EXISTS exam_attempt (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '一次考试尝试ID',
    exam_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    attempt_no INT NOT NULL DEFAULT 1,
    start_time DATETIME NOT NULL,
    submit_time DATETIME NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS/SUBMITTED/GRADED/PUBLISHED',
    objective_score DECIMAL(8,2) NOT NULL DEFAULT 0,
    subjective_score DECIMAL(8,2) NOT NULL DEFAULT 0,
    total_score DECIMAL(8,2) NOT NULL DEFAULT 0,
    cheat_count INT NOT NULL DEFAULT 0,
    last_saved_at DATETIME NULL,
    version_no BIGINT NOT NULL DEFAULT 0 COMMENT '答案版本号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_attempt_exam_user_no (exam_id, user_id, attempt_no),
    KEY idx_attempt_exam_user (exam_id, user_id),
    KEY idx_attempt_status (status),
    CONSTRAINT fk_attempt_exam FOREIGN KEY (exam_id) REFERENCES exam(id),
    CONSTRAINT fk_attempt_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB COMMENT='考生考试记录';

CREATE TABLE IF NOT EXISTS answer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '答题记录ID',
    attempt_id BIGINT NOT NULL,
    paper_question_id BIGINT NOT NULL,
    answer_content JSON NULL COMMENT '学生答案JSON',
    is_correct TINYINT NULL COMMENT '1正确 0错误 NULL未判定',
    auto_score DECIMAL(8,2) NOT NULL DEFAULT 0,
    manual_score DECIMAL(8,2) NOT NULL DEFAULT 0,
    final_score DECIMAL(8,2) NOT NULL DEFAULT 0,
    teacher_comment VARCHAR(1000) NULL,
    answered_at DATETIME NULL,
    graded_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_answer_attempt_question (attempt_id, paper_question_id),
    KEY idx_answer_paper_question (paper_question_id),
    KEY idx_answer_attempt (attempt_id),
    CONSTRAINT fk_answer_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempt(id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_paper_question FOREIGN KEY (paper_question_id) REFERENCES paper_question(id)
) ENGINE=InnoDB COMMENT='考生答题记录';

-- ------------------------------------------------------------
-- 6. 刷题记录、错题、收藏、笔记
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS practice_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    session_type VARCHAR(30) NOT NULL COMMENT 'CHAPTER/RANDOM/SIMULATION等',
    answer_content JSON NULL,
    is_correct TINYINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_practice_user_time (user_id, created_at),
    KEY idx_practice_user_question (user_id, question_id),
    KEY idx_practice_question_correct (question_id, is_correct),
    CONSTRAINT fk_practice_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_practice_question FOREIGN KEY (question_id) REFERENCES question(id)
) ENGINE=InnoDB COMMENT='用户刷题记录';

-- ------------------------------------------------------------
-- 6.1 错题、收藏、笔记
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wrong_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    wrong_count INT NOT NULL DEFAULT 1,
    redo_count INT NOT NULL DEFAULT 0,
    mastery_level TINYINT NOT NULL DEFAULT 0 COMMENT '0未掌握 1练习中 2基本掌握 3已掌握',
    last_wrong_at DATETIME NULL,
    last_correct_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wrong_question_user_question (user_id, question_id),
    KEY idx_wrong_question_user_mastery (user_id, mastery_level),
    CONSTRAINT fk_wrong_question_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_wrong_question_question FOREIGN KEY (question_id) REFERENCES question(id)
) ENGINE=InnoDB COMMENT='用户错题';

CREATE TABLE IF NOT EXISTS favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_favorite_user_question (user_id, question_id),
    KEY idx_favorite_user_created (user_id, created_at),
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_question FOREIGN KEY (question_id) REFERENCES question(id)
) ENGINE=InnoDB COMMENT='题目收藏';

CREATE TABLE IF NOT EXISTS question_note (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_question_note_user_question (user_id, question_id),
    CONSTRAINT fk_question_note_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_question_note_question FOREIGN KEY (question_id) REFERENCES question(id)
) ENGINE=InnoDB COMMENT='题目笔记';

-- ------------------------------------------------------------
-- 7. 考试行为与系统日志
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL COMMENT 'PAGE_HIDDEN/PAGE_VISIBLE等',
    event_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_info VARCHAR(1000) NULL,
    metadata JSON NULL,
    KEY idx_exam_event_attempt_time (attempt_id, event_time),
    KEY idx_exam_event_user_type (user_id, event_type),
    CONSTRAINT fk_exam_event_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempt(id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_event_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB COMMENT='考试行为事件';

CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NULL,
    module VARCHAR(50) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    request_method VARCHAR(10) NULL,
    request_uri VARCHAR(500) NULL,
    request_params JSON NULL,
    result_code INT NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_operation_log_user_time (user_id, created_at),
    KEY idx_operation_log_module_time (module, created_at),
    CONSTRAINT fk_operation_log_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='系统操作日志';

SET FOREIGN_KEY_CHECKS = 1;

-- ------------------------------------------------------------
-- 初始化角色
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role (role_code, role_name, description) VALUES
('STUDENT', '学生', '参加刷题和考试'),
('TEACHER', '教师', '题库、试卷、考试和阅卷管理'),
('ADMIN', '管理员', '系统全局管理');

-- ------------------------------------------------------------
-- 初始化权限
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_permission (permission_code, permission_name, resource, action, description) VALUES
('profile:view', '查看个人资料', 'profile', 'view', '查看本人资料'),
('profile:update', '修改个人资料', 'profile', 'update', '修改本人资料'),
('question:view', '查看题目', 'question', 'view', '浏览题库'),
('question:create', '创建题目', 'question', 'create', '新增题目'),
('question:update', '修改题目', 'question', 'update', '修改题目'),
('question:delete', '删除题目', 'question', 'delete', '删除题目'),
('question:import', '导入题目', 'question', 'import', 'Excel/JSON导入'),
('question:export', '导出题目', 'question', 'export', 'Excel/JSON导出'),
('practice:start', '开始练习', 'practice', 'start', '开始刷题'),
('wrong-question:view', '查看错题', 'wrong-question', 'view', '查看本人错题'),
('wrong-question:update', '更新错题状态', 'wrong-question', 'update', '更新掌握度'),
('favorite:manage', '管理收藏', 'favorite', 'manage', '收藏与取消收藏'),
('note:manage', '管理笔记', 'note', 'manage', '题目笔记'),
('paper:view', '查看试卷', 'paper', 'view', '查看试卷'),
('paper:create', '创建试卷', 'paper', 'create', '固定试卷创建'),
('paper:update', '修改试卷', 'paper', 'update', '修改试卷'),
('paper:generate', '动态组卷', 'paper', 'generate', '规则组卷'),
('paper:publish', '发布试卷', 'paper', 'publish', '发布试卷'),
('exam:view', '查看考试', 'exam', 'view', '查看考试'),
('exam:create', '创建考试', 'exam', 'create', '创建考试'),
('exam:update', '修改考试', 'exam', 'update', '修改考试'),
('exam:publish', '发布考试', 'exam', 'publish', '发布考试'),
('exam:join', '参加考试', 'exam', 'join', '学生参加考试'),
('exam:save', '保存答案', 'exam', 'save', '保存作答进度'),
('exam:submit', '提交试卷', 'exam', 'submit', '提交最终答卷'),
('grade:view', '查看成绩', 'grade', 'view', '查看成绩'),
('grade:judge', '人工阅卷', 'grade', 'judge', '批改主观题'),
('grade:publish', '发布成绩', 'grade', 'publish', '发布最终成绩'),
('statistics:view:self', '查看个人统计', 'statistics', 'view_self', '查看个人学习分析'),
('statistics:view:exam', '查看考试统计', 'statistics', 'view_exam', '查看考试分析'),
('user:view', '查看用户', 'user', 'view', '查看用户列表'),
('user:create', '创建用户', 'user', 'create', '管理员创建用户'),
('user:update', '修改用户', 'user', 'update', '修改用户'),
('user:disable', '禁用用户', 'user', 'disable', '启用或禁用用户'),
('role:manage', '管理角色', 'role', 'manage', '角色管理'),
('permission:manage', '管理权限', 'permission', 'manage', '权限管理'),
('subject:manage', '管理学科', 'subject', 'manage', '学科管理'),
('knowledge:manage', '管理知识点', 'knowledge', 'manage', '知识点管理'),
('log:view', '查看系统日志', 'log', 'view', '查看操作与考试日志');

-- ------------------------------------------------------------
-- 角色权限初始化：学生
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'profile:view','profile:update',
    'question:view','practice:start',
    'wrong-question:view','wrong-question:update',
    'favorite:manage','note:manage',
    'paper:view','exam:view','exam:join','exam:save','exam:submit',
    'grade:view','statistics:view:self'
)
WHERE r.role_code = 'STUDENT';

-- ------------------------------------------------------------
-- 角色权限初始化：教师
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'profile:view','profile:update',
    'question:view','question:create','question:update','question:delete','question:import','question:export',
    'paper:view','paper:create','paper:update','paper:generate','paper:publish',
    'exam:view','exam:create','exam:update','exam:publish',
    'grade:view','grade:judge','grade:publish',
    'statistics:view:exam'
)
WHERE r.role_code = 'TEACHER';

-- ------------------------------------------------------------
-- 角色权限初始化：管理员拥有全部权限
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN';

-- ------------------------------------------------------------
-- 初始化学科/知识点示例
-- ------------------------------------------------------------
-- 示例数据带 NOT EXISTS 守卫，重复执行不会插入重复行。
INSERT INTO subject (name, description, status, sort)
SELECT t.name, t.description, t.status, t.sort
FROM (
    SELECT '计算机基础' AS name, '计算机相关基础知识题库' AS description, 1 AS status, 1 AS sort
    UNION ALL SELECT 'Java程序设计', 'Java语言与面向对象相关知识', 1, 2
    UNION ALL SELECT '数据库原理', '关系数据库与SQL相关知识', 1, 3
) t
WHERE NOT EXISTS (SELECT 1 FROM subject);

INSERT INTO knowledge_point (subject_id, parent_id, name, description, sort)
SELECT id, NULL, 'Java基础', 'Java基本语法与基础概念', 1
FROM subject WHERE name='Java程序设计'
  AND NOT EXISTS (SELECT 1 FROM knowledge_point k WHERE k.name='Java基础');

INSERT INTO knowledge_point (subject_id, parent_id, name, description, sort)
SELECT s.id, kp.id, '面向对象', '类、对象、继承、多态、封装', 1
FROM subject s
JOIN knowledge_point kp ON kp.subject_id=s.id AND kp.name='Java基础'
WHERE s.name='Java程序设计'
  AND NOT EXISTS (SELECT 1 FROM knowledge_point k WHERE k.subject_id=s.id AND k.name='面向对象');

INSERT INTO knowledge_point (subject_id, parent_id, name, description, sort)
SELECT s.id, kp.id, '集合框架', 'List、Set、Map等集合框架', 2
FROM subject s
JOIN knowledge_point kp ON kp.subject_id=s.id AND kp.name='Java基础'
WHERE s.name='Java程序设计'
  AND NOT EXISTS (SELECT 1 FROM knowledge_point k WHERE k.subject_id=s.id AND k.name='集合框架');

-- ------------------------------------------------------------
-- 注意：初始管理员/教师账号建议由 Spring Boot 启动时按 BCrypt 动态初始化，
-- 不在此脚本中硬编码明文密码。
-- ============================================================
