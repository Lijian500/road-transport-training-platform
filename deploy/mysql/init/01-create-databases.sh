#!/bin/bash
set -euo pipefail

required_identifier='^[A-Za-z0-9_]+$'
admin_database="${MYSQL_ADMIN_DATABASE:-road_training_admin}"
training_database="${MYSQL_TRAINING_DATABASE:-road_training_training}"
learning_database="${MYSQL_LEARNING_DATABASE:-road_training_learning}"
admin_user="${MYSQL_ADMIN_USER:-train_admin}"
training_user="${MYSQL_TRAINING_USER:-train_training}"
learning_user="${MYSQL_LEARNING_USER:-train_learning}"

for database_name in "${admin_database}" "${training_database}" "${learning_database}"; do
  if [[ ! "${database_name}" =~ ${required_identifier} ]]; then
    echo "数据库名称仅允许字母、数字和下划线：${database_name}" >&2
    exit 1
  fi
done

for application_user in "${admin_user}" "${training_user}" "${learning_user}"; do
  if [[ ! "${application_user}" =~ ${required_identifier} ]]; then
    echo "数据库用户名仅允许字母、数字和下划线：${application_user}" >&2
    exit 1
  fi
done

: "${MYSQL_ADMIN_PASSWORD:?MYSQL_ADMIN_PASSWORD 不能为空}"
: "${MYSQL_TRAINING_PASSWORD:?MYSQL_TRAINING_PASSWORD 不能为空}"
: "${MYSQL_LEARNING_PASSWORD:?MYSQL_LEARNING_PASSWORD 不能为空}"

admin_password="${MYSQL_ADMIN_PASSWORD//\'/\'\'}"
training_password="${MYSQL_TRAINING_PASSWORD//\'/\'\'}"
learning_password="${MYSQL_LEARNING_PASSWORD//\'/\'\'}"

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-SQL
CREATE DATABASE IF NOT EXISTS \`${admin_database}\`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS \`${training_database}\`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS \`${learning_database}\`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS '${admin_user}'@'%' IDENTIFIED BY '${admin_password}';
ALTER USER '${admin_user}'@'%' IDENTIFIED BY '${admin_password}';
CREATE USER IF NOT EXISTS '${training_user}'@'%' IDENTIFIED BY '${training_password}';
ALTER USER '${training_user}'@'%' IDENTIFIED BY '${training_password}';
CREATE USER IF NOT EXISTS '${learning_user}'@'%' IDENTIFIED BY '${learning_password}';
ALTER USER '${learning_user}'@'%' IDENTIFIED BY '${learning_password}';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP,
      REFERENCES, CREATE TEMPORARY TABLES, LOCK TABLES, EXECUTE
  ON \`${admin_database}\`.* TO '${admin_user}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP,
      REFERENCES, CREATE TEMPORARY TABLES, LOCK TABLES, EXECUTE
  ON \`${training_database}\`.* TO '${training_user}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP,
      REFERENCES, CREATE TEMPORARY TABLES, LOCK TABLES, EXECUTE
  ON \`${learning_database}\`.* TO '${learning_user}'@'%';
SQL

echo "三个业务逻辑数据库及各自的最小权限账号初始化完成。"
