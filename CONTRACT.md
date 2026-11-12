# 师生业绩管理系统：协作约定

技术栈：Java 21 / Spring Boot 4.x / Spring Security / MyBatis / H2本地演示与MySQL部署，Vue3 / TypeScript / Vite / Element Plus / ECharts。

## API（前后端共同遵守）
- 所有接口位于 `/api`，返回普通 JSON（不包 data）。错误为 `{message:string}`。列表为 `{items:[],total:number,page:number,size:number}`，page从1开始。
- Session登录。GET `/auth/csrf` -> `{token,headerName}`；写请求先获取token并在headerName指定请求头中携带。GET `/auth/me` -> User，未登录401。POST `/auth/login` `{username,password}` -> User。POST `/auth/logout` -> 204。
- User: `{id,username,name,role:'ADMIN'|'TEACHER'|'STUDENT',number,cohort,department,enabled}`。GET `/users/options` -> 可选参与人User[]；管理员GET `/users`分页、POST `/users`新增、PUT `/users/{id}`更新（可选password）；个人PUT `/auth/password` `{oldPassword,newPassword}`。
- Type: TEACHING/RESEARCH/COMPETITION/SCHOLARSHIP/PATENT/PAPER/SOFTWARE/CERTIFICATION。Level: INTERNATIONAL/NATIONAL/PROVINCIAL/UNIVERSITY/OTHER。Status: DRAFT/PENDING/APPROVED/REJECTED。
- Achievement: `{id,title,type,level,achievedOn:'YYYY-MM-DD',description,details:{},status,ownerId,ownerName,createdAt,updatedAt,participants:[{userId,name,role:'AUTHOR'|'MEMBER'|'ADVISOR',sortOrder}],indicatorIds:number[]}`。
- GET `/achievements` 分页，query: page,size,q,type,level,status,year,indicatorId,scope('mine'|'guided'|'all')。普通用户只能看自己拥有/参与/指导的记录，ADMIN可看全部。GET `/achievements/{id}`详情。POST `/achievements` 与 PUT `/achievements/{id}` 接受 `{title,type,level,achievedOn,description,details,participants:[{userId,role,sortOrder}],indicatorIds}`。只有所有者或管理员可编辑草稿/退回记录。DELETE `/achievements/{id}`同权限。
- POST `/achievements/{id}/submit`；管理员POST `/achievements/{id}/review` `{approved:boolean,comment:string}`，禁止自审；GET `/achievements/{id}/reviews` -> 审核记录数组 `{id,reviewerName,approved,comment,createdAt}`。
- GET `/achievements/{id}/attachments` -> `[{id,originalName,contentType,size,createdAt}]`。POST同地址 multipart `file`。GET `/attachments/{id}/download` 二进制。DELETE `/attachments/{id}`。附件权限跟随成果，可写状态仅DRAFT/REJECTED。
- GET `/indicators` -> `[{id,code,name,description}]`；管理员POST `/indicators`，PUT `/indicators/{id}`。
- GET `/stats?year=` -> `{total,participants,teachers,students,pending,byType:[{name,value}],byLevel:[{name,value}],byYear:[{name,value}],byMonth:[{name,value}],topContributors:[{name,value}]}`。正式统计仅APPROVED，普通用户范围同成果可见范围。
- GET `/exports/achievements.xlsx`，GET `/exports/materials.zip`，支持q,type,level,status,year,indicatorId,scope，同成果查询权限；导出默认APPROVED。

## 数据与权限
- 单成果、多参与/指导关系，统计distinct成果ID，不把成员人次当成果数。
- 只有所有者/管理员可修改草稿/退回成果。待审和已通过内容不能直接修改。所有材料私有存储。服务端校验全部访问范围。
- 种子数据：管理员admin / Admin@12345；教师teacher / Teacher@12345；学生student / Student@12345。仅用于本地演示，可配置关闭演示种子。请勿将演示账户用于公开部署。
- 前端代理 `/api` -> `http://127.0.0.1:8080`。生产构建复制到后端static，或由Nginx代理。
- 开发分工：backend/归后端agent；frontend/归前端agent；docs/与部署脚本归文档agent；根代理负责集成、端到端验证和修复。
