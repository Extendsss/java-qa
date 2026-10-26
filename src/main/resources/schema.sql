CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username    VARCHAR(50)  NOT NULL                COMMENT '登录名，唯一',
  password    VARCHAR(100) NOT NULL                COMMENT 'BCrypt 哈希，不存明文',
  nickname    VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '昵称',
  role        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER',
  status      TINYINT      NOT NULL DEFAULT 1      COMMENT '1正常 0禁用',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 题目分类表
CREATE TABLE IF NOT EXISTS question_category (
  status      TINYINT NOT NULL DEFAULT 1,
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(50) NOT NULL                COMMENT '分类名，唯一',
  sort        INT         NOT NULL DEFAULT 0      COMMENT '排序值，小的在前',
  create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目分类表';

-- 分块定义表（简历模板的构成单元，总数 ≤ 20）
CREATE TABLE IF NOT EXISTS block_def (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  code         VARCHAR(50) NOT NULL                COMMENT '分块编码，唯一（如 project）',
  name         VARCHAR(50) NOT NULL                COMMENT '分块名（如 项目经历）',
  field_schema JSON        NOT NULL                COMMENT '字段定义数组 [{key,label,type,required}]，type∈text/textarea/date/list',
  repeatable   TINYINT     NOT NULL DEFAULT 0      COMMENT '1可多条（项目/教育/工作）0单条',
  enabled      TINYINT     NOT NULL DEFAULT 1      COMMENT '1纳入模板展示 0停用（只影响录入表单）',
  sort         INT         NOT NULL DEFAULT 0      COMMENT '模板内顺序，小的在前',
  built_in     TINYINT     NOT NULL DEFAULT 0      COMMENT '1预置分块 0自定义分块',
  status       TINYINT     NOT NULL DEFAULT 1      COMMENT '1正常 0软删除',
  create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历分块定义表';

-- 简历表（结构化分块简历）
CREATE TABLE IF NOT EXISTS resume (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  title       VARCHAR(200) NOT NULL                COMMENT '简历标题',
  owner_id    BIGINT       NOT NULL                COMMENT '上传人用户ID（含管理员）',
  is_public   TINYINT      NOT NULL DEFAULT 0      COMMENT '1公共 0私有',
  status      TINYINT      NOT NULL DEFAULT 1      COMMENT '1正常 0软删除',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_owner (owner_id),
  KEY idx_public (is_public)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历表（结构化分块）';

-- 简历内容表（一条 = 某分块的一个条目，内容按 field_schema 存 JSON）
CREATE TABLE IF NOT EXISTS resume_block (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  resume_id   BIGINT       NOT NULL                COMMENT '所属简历ID',
  block_def_id BIGINT      NOT NULL                COMMENT '分块类型ID',
  sort        INT          NOT NULL DEFAULT 0      COMMENT '同分块内条目顺序（可重复分块多条排序）',
  content     JSON         NOT NULL                COMMENT '字段值 {"项目名":"...","详情":["...","..."]}，结构由 field_schema 定义',
  status      TINYINT      NOT NULL DEFAULT 1      COMMENT '1正常 0软删除',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_resume (resume_id),
  KEY idx_def (block_def_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历内容（分块条目）表';

-- 题目表（含问题链与简历专项）
CREATE TABLE IF NOT EXISTS question (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  category_id BIGINT       NOT NULL                COMMENT '分类ID',
  type        VARCHAR(20)  NOT NULL                COMMENT '题型：QA/SINGLE/MULTI/JUDGE/FILL',
  difficulty  TINYINT      NOT NULL DEFAULT 1      COMMENT '难度：1简单 2中等 3困难',
  title       VARCHAR(200) NOT NULL                COMMENT '题干标题',
  content     TEXT         NOT NULL                COMMENT '题干详细内容（问答题的问题描述）',
  options     JSON         NULL                    COMMENT '选项数组 [{"key":"A","text":"..."}]，JUDGE/FILL/QA 为 NULL',
  answer      TEXT         NOT NULL                COMMENT '标准答案/参考答案，约定见 2.3',
  analysis    TEXT         NULL                    COMMENT '答案解析',
  parent_id   BIGINT       NULL                    COMMENT '父题ID（本题为追问时指向父题），NULL 表示独立题/链首',
  root_id     BIGINT       NULL                    COMMENT '链首题目ID，NULL 表示自身即链首；链首=COALESCE(root_id,id)',
  resume_id   BIGINT       NULL                    COMMENT '所属简历ID（简历专项题），NULL 为普通题；归属强制随简历',
  block_id    BIGINT       NULL                    COMMENT '关联简历分块条目ID（resume_block.id，仅简历专项题用，可空）',
  owner_id    BIGINT       NOT NULL                COMMENT '录入人用户ID（含管理员）；追问/简历题强制继承',
  is_public   TINYINT      NOT NULL DEFAULT 0      COMMENT '1公共题 0私有题；追问/简历题强制继承',
  status      TINYINT      NOT NULL DEFAULT 1      COMMENT '1正常 0软删除',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_owner (owner_id),
  KEY idx_root (root_id),
  KEY idx_parent (parent_id),
  KEY idx_resume (resume_id),
  KEY idx_block (block_id),
  KEY idx_public_cat (is_public, category_id),
  KEY idx_type_diff (type, difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目表（含问题链与简历专项）';

-- 答题记录表
CREATE TABLE IF NOT EXISTS answer_record (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT      NOT NULL                COMMENT '答题用户ID',
  question_id BIGINT      NOT NULL                COMMENT '题目ID',
  user_answer TEXT        NULL                    COMMENT '用户作答内容（问答题可为空）',
  is_correct  TINYINT     NOT NULL                COMMENT '1正确/掌握 0错误/未掌握',
  create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
  PRIMARY KEY (id),
  KEY idx_user_question (user_id, question_id),
  KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='答题记录表';

-- 评论表
CREATE TABLE IF NOT EXISTS comment (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  question_id BIGINT       NOT NULL                COMMENT '题目ID',
  user_id     BIGINT       NOT NULL                COMMENT '评论用户ID',
  content     VARCHAR(300) NOT NULL                COMMENT '评论内容',
  status      TINYINT      NOT NULL DEFAULT 1      COMMENT '1可见 0软删除',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  PRIMARY KEY (id),
  KEY idx_question (question_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目评论表';
