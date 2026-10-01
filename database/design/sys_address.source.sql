-- 来源：用户于 2026-09-30 提供的 sys_address 真实 DDL；原样保留建表定义。
-- 仅用于论文与数据字典核验，不是 Flyway 迁移，不执行此文件。
CREATE TABLE `sys_address` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `level` tinyint unsigned NOT NULL COMMENT '层级',
  `parent_code` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '0' COMMENT '父级行政代码',
  `area_code` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '0' COMMENT '行政代码',
  `zip_code` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '0' COMMENT '邮政编码',
  `city_code` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '' COMMENT '区号',
  `name` varchar(50) NOT NULL DEFAULT '' COMMENT '名称',
  `short_name` varchar(50) NOT NULL DEFAULT '' COMMENT '简称',
  `merger_name` varchar(50) NOT NULL DEFAULT '' COMMENT '组合名',
  `pinyin` varchar(30) NOT NULL DEFAULT '' COMMENT '拼音',
  `lng` decimal(10,6) NOT NULL DEFAULT '0.000000' COMMENT '经度',
  `lat` decimal(10,6) NOT NULL DEFAULT '0.000000' COMMENT '纬度',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`area_code`) USING BTREE,
  KEY `idx_parent_code` (`parent_code`) USING BTREE
) ENGINE=MyISAM AUTO_INCREMENT=4063 DEFAULT CHARSET=utf8mb3 COMMENT='中国行政地区表';
