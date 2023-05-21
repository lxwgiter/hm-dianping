# 黑马点评项目代码

## 本项目需要配置的环境
- Mysql数据库环境：hmdp.sql
- linux运行redis环境
- nginx部署前端项目
- 各框架版本见pom文件

## 配置文件
- application.yaml
  -  Mysql环境配置
  -  redis环境配置

- config/RedissonConfig.java
  - 配置Redisson

## 缓存预热
- 测试类中testSaveShop方法，预热逻辑过期店铺
- 测试类中loadShopData或loadShopData2方法，预热Geo数据
