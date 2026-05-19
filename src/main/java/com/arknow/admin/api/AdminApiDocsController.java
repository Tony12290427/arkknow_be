package com.arknow.admin.api;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/api-docs")
public class AdminApiDocsController {

    @GetMapping
    public Map<String, Object> list() {
        List<Map<String, Object>> modules = new ArrayList<>();

        // Auth
        modules.add(module("Auth", "用户认证与账户管理",
            endpoint("POST", "/api/v1/auth/send-code", "发送手机/邮箱验证码", false,
                map("identifierType", "PHONE", "identifier", "13800000000", "scene", "REGISTER"),
                map("identifier", "13800000000", "scene", "REGISTER", "expireSeconds", 300)),
            endpoint("POST", "/api/v1/auth/register", "用户注册", false,
                map("identifierType", "PHONE", "identifier", "13800000000", "code", "123456", "password", "Test1234", "agreeTerms", true),
                map("token", map("accessToken", "...", "refreshToken", "..."), "user", map("id", 1, "nickname", "知舟用户xxx"))),
            endpoint("POST", "/api/v1/auth/login", "用户登录（密码或验证码）", false,
                map("identifierType", "PHONE", "identifier", "13800000000", "password", "Test1234"),
                map("token", map("accessToken", "...", "refreshToken", "..."), "user", map("id", 1, "nickname", "..."))),
            endpoint("POST", "/api/v1/auth/token/refresh", "刷新Token", false,
                map("refreshToken", "..."),
                map("accessToken", "...", "refreshToken", "...")),
            endpoint("POST", "/api/v1/auth/logout", "登出", true,
                map("refreshToken", "..."),
                null),
            endpoint("GET", "/api/v1/auth/me", "获取当前用户信息", true, null,
                map("id", 1, "nickname", "知舟用户xxx", "phone", "138xxxx", "email", null, "avatar", null)),
            endpoint("POST", "/api/v1/auth/password/change", "修改密码", true,
                map("oldPassword", "OldPass1234", "newPassword", "NewPass5678"),
                null),
            endpoint("POST", "/api/v1/auth/password/reset", "重置密码", false,
                map("identifierType", "PHONE", "identifier", "13800000000", "code", "123456", "newPassword", "NewPass5678"),
                null),
            endpoint("POST", "/api/v1/auth/email/bind", "绑定邮箱", true,
                map("email", "test@example.com", "code", "123456"),
                null),
            endpoint("POST", "/api/v1/auth/email/unbind", "解绑邮箱", true, null, null),
            endpoint("DELETE", "/api/v1/auth/account", "注销账号", true, null, null)
        ));

        // Posts
        modules.add(module("KnowPosts", "知识帖子管理",
            endpoint("GET", "/api/v1/knowposts/feed", "公开Feed流（分页）", false, null,
                map("items", List.of(), "page", 1, "size", 20, "total", 130, "hasMore", true)),
            endpoint("GET", "/api/v1/knowposts/detail/{id}", "帖子详情", false, null,
                map("id", "...", "title", "...", "content", "...", "imgUrls", "...", "authorNickname", "...")),
            endpoint("GET", "/api/v1/knowposts/mine", "我的帖子", true, null, null),
            endpoint("GET", "/api/v1/knowposts/following", "关注用户的帖子", true, null, null),
            endpoint("GET", "/api/v1/knowposts/liked", "我点赞的帖子", true, null, null),
            endpoint("GET", "/api/v1/knowposts/faved", "我收藏的帖子", true, null, null),
            endpoint("POST", "/api/v1/knowposts/drafts", "创建草稿", true, null,
                map("id", "...")),
            endpoint("POST", "/api/v1/knowposts/{id}/content/confirm", "确认帖子内容", true,
                map("title", "标题", "content", "Markdown正文", "imgUrls", "[\"url1\",\"url2\"]", "tags", "[\"标签1\"]"),
                null),
            endpoint("POST", "/api/v1/knowposts/{id}/publish", "发布帖子", true, null, null),
            endpoint("PATCH", "/api/v1/knowposts/{id}", "更新帖子元数据", true,
                map("title", "新标题"),
                null),
            endpoint("DELETE", "/api/v1/knowposts/{id}", "删除帖子（软删除）", true, null, null)
        ));

        // Comments
        modules.add(module("Comments", "评论管理",
            endpoint("GET", "/api/v1/comments", "获取帖子评论列表", false, null,
                map("items", List.of(), "total", 0, "offset", 0)),
            endpoint("POST", "/api/v1/comments", "创建评论", true,
                map("postId", 1, "content", "评论内容", "parentId", null),
                map("id", "...", "content", "评论内容")),
            endpoint("DELETE", "/api/v1/comments/{id}", "删除自己的评论", true, null,
                map("success", true)),
            endpoint("GET", "/api/v1/comments/{id}/replies", "获取评论回复", false, null,
                map("items", List.of(), "total", 0, "hasMore", false))
        ));

        // Relations
        modules.add(module("Relations", "用户关注关系",
            endpoint("POST", "/api/v1/relation/follow", "关注用户", true, null,
                map("success", true)),
            endpoint("POST", "/api/v1/relation/unfollow", "取消关注", true, null,
                map("success", true)),
            endpoint("GET", "/api/v1/relation/status", "获取关注状态", true, null,
                map("status", "none|following|followedBy|mutual")),
            endpoint("GET", "/api/v1/relation/following", "关注列表", false, null,
                List.of(map("id", 1, "nickname", "..."))),
            endpoint("GET", "/api/v1/relation/followers", "粉丝列表", false, null,
                List.of(map("id", 1, "nickname", "...")))
        ));

        // Actions
        modules.add(module("Actions", "点赞/收藏操作",
            endpoint("POST", "/api/v1/action/like", "点赞", true,
                map("entityType", "POST", "entityId", "..."),
                map("changed", true, "liked", true)),
            endpoint("POST", "/api/v1/action/unlike", "取消点赞", true,
                map("entityType", "POST", "entityId", "..."),
                map("changed", true, "liked", false)),
            endpoint("POST", "/api/v1/action/fav", "收藏", true,
                map("entityType", "POST", "entityId", "..."),
                map("changed", true, "faved", true)),
            endpoint("POST", "/api/v1/action/unfav", "取消收藏", true,
                map("entityType", "POST", "entityId", "..."),
                map("changed", true, "faved", false))
        ));

        // Collections
        modules.add(module("Collections", "收藏夹管理",
            endpoint("GET", "/api/v1/collections", "收藏夹列表", true, null,
                List.of(map("id", 1, "name", "默认收藏夹", "item_count", 5))),
            endpoint("POST", "/api/v1/collections", "创建收藏夹", true,
                map("name", "新收藏夹"),
                map("id", "...", "name", "新收藏夹")),
            endpoint("DELETE", "/api/v1/collections/{id}", "删除收藏夹", true, null,
                map("success", true)),
            endpoint("GET", "/api/v1/collections/{id}/items", "收藏夹内容", true, null, List.of()),
            endpoint("POST", "/api/v1/collections/{id}/items", "添加帖子到收藏夹", true,
                map("postId", 1),
                map("success", true)),
            endpoint("DELETE", "/api/v1/collections/{id}/items/{postId}", "从收藏夹移除", true, null,
                map("success", true))
        ));

        // Notifications
        modules.add(module("Notifications", "通知管理",
            endpoint("GET", "/api/v1/notifications", "通知列表", true, null,
                map("items", List.of(), "unread", 0)),
            endpoint("GET", "/api/v1/notifications/unread-count", "未读通知数", true, null,
                map("count", 0)),
            endpoint("POST", "/api/v1/notifications/read-all", "全部标记已读", true, null,
                map("success", true)),
            endpoint("DELETE", "/api/v1/notifications/{id}", "删除通知", true, null,
                map("success", true))
        ));

        // Search
        modules.add(module("Search", "搜索",
            endpoint("GET", "/api/v1/search", "关键词搜索", false,
                map("keyword", "技术", "page", 1, "size", 20),
                List.of()),
            endpoint("GET", "/api/v1/search/suggest", "搜索建议", false,
                map("prefix", "技", "size", 5),
                map("suggestions", List.of())),
            endpoint("GET", "/api/v1/search/ai", "AI智能搜索（SSE流式）", false,
                map("q", "如何学习编程", "topK", 5),
                "SSE Stream: data: [HTML]... data: [ARTICLES]... data: [DONE]")
        ));

        // Profile
        modules.add(module("Profile", "用户资料",
            endpoint("GET", "/api/v1/profile", "获取自己的资料", true, null,
                map("id", 1, "nickname", "...", "avatar", null, "bio", null, "gender", null)),
            endpoint("GET", "/api/v1/profile/{userId}", "获取公开资料", false, null,
                map("id", 1, "nickname", "...")),
            endpoint("PATCH", "/api/v1/profile", "更新资料", true,
                map("nickname", "新昵称", "bio", "个人简介"),
                map("id", 1, "nickname", "新昵称")),
            endpoint("POST", "/api/v1/profile/avatar", "上传头像", true, null, null),
            endpoint("GET", "/api/v1/profile/{userId}/tags", "获取个性标签", false, null,
                List.of("MBTI:INTJ", "兴趣:编程")),
            endpoint("PUT", "/api/v1/profile/{userId}/tags", "更新个性标签", true,
                map("tags", List.of("MBTI:INTJ", "兴趣:编程")),
                null)
        ));

        // Counter
        modules.add(module("Counter", "计数统计",
            endpoint("GET", "/api/v1/counter/user/{userId}", "用户统计数据", false, null,
                map("followings", 10, "followers", 5, "posts", 20, "likedPosts", 30))
        ));

        // Storage
        modules.add(module("Storage", "文件上传",
            endpoint("POST", "/api/v1/storage/presign", "获取OSS预签名上传URL", true,
                map("filename", "image.png", "contentType", "image/png"),
                map("url", "https://oss-cn-xxx/...", "publicUrl", "https://...image.png")),
            endpoint("POST", "/api/v1/storage/local/upload", "本地上传文件", true, null,
                map("url", "/uploads/xxx.png"))
        ));

        // Admin Auth
        modules.add(module("Admin Auth", "管理员认证",
            endpoint("POST", "/api/v1/admin/auth/login", "管理员登录", false,
                map("identifierType", "PHONE", "identifier", "13800000000", "code", "123456"),
                map("token", map("accessToken", "..."), "user", map("id", 1, "nickname", "管理员"))),
            endpoint("POST", "/api/v1/admin/auth/refresh", "刷新Admin Token", false, null, null),
            endpoint("POST", "/api/v1/admin/auth/logout", "管理员登出", true, null, null),
            endpoint("GET", "/api/v1/admin/auth/me", "当前管理员信息", true, null, null)
        ));

        // Admin Users
        modules.add(module("Admin Users", "管理员-用户管理",
            endpoint("GET", "/api/v1/admin/users", "用户列表（分页+搜索）", true, null,
                map("items", List.of(), "total", 10, "page", 1, "size", 20)),
            endpoint("GET", "/api/v1/admin/users/{id}", "用户详情", true, null, null),
            endpoint("PUT", "/api/v1/admin/users/{id}", "更新用户（封禁/解封）", true,
                map("role", "USER"),
                map("success", true)),
            endpoint("DELETE", "/api/v1/admin/users/{id}", "删除用户", true, null,
                map("success", true)),
            endpoint("POST", "/api/v1/admin/users/batch-delete", "批量删除用户", true,
                map("ids", List.of(1, 2, 3)),
                map("success", true, "deleted", 3))
        ));

        // Admin Posts
        modules.add(module("Admin Posts", "管理员-帖子管理",
            endpoint("GET", "/api/v1/admin/posts", "帖子列表（分页+状态筛选）", true, null,
                map("items", List.of(), "total", 130, "page", 1, "size", 20)),
            endpoint("GET", "/api/v1/admin/posts/{id}", "帖子详情", true, null, null),
            endpoint("DELETE", "/api/v1/admin/posts/{id}", "删除帖子", true, null,
                map("success", true)),
            endpoint("POST", "/api/v1/admin/posts/batch-delete", "批量删除帖子", true,
                map("ids", List.of(1, 2, 3)),
                map("success", true)),
            endpoint("POST", "/api/v1/admin/posts/{id}/audit", "审核帖子（approve/reject）", true,
                map("action", "approve"),
                map("success", true))
        ));

        // Admin remaining controllers (compact)
        modules.add(module("Admin Comments", "管理员-评论管理",
            endpoint("GET", "/api/v1/admin/comments", "评论列表", true, null, null),
            endpoint("DELETE", "/api/v1/admin/comments/{id}", "删除评论", true, null, map("success", true)),
            endpoint("POST", "/api/v1/admin/comments/batch-delete", "批量删除评论", true, null, map("success", true))
        ));

        modules.add(module("Admin Tags", "管理员-标签管理",
            endpoint("GET", "/api/v1/admin/tags", "标签列表", true, null, null)
        ));

        modules.add(module("Admin Categories", "管理员-分类管理",
            endpoint("GET", "/api/v1/admin/categories", "分类列表", true, null, null)
        ));

        modules.add(module("Admin Collections", "管理员-收藏夹管理",
            endpoint("GET", "/api/v1/admin/collections", "收藏夹列表", true, null, null),
            endpoint("DELETE", "/api/v1/admin/collections/{id}", "删除收藏夹", true, null, map("success", true))
        ));

        modules.add(module("Admin Follows", "管理员-关注管理",
            endpoint("GET", "/api/v1/admin/follows", "关注列表", true, null, null),
            endpoint("DELETE", "/api/v1/admin/follows/{id}", "删除关注关系", true, null, map("success", true))
        ));

        modules.add(module("Admin Notifications", "管理员-通知管理",
            endpoint("GET", "/api/v1/admin/notifications", "通知列表", true, null, null),
            endpoint("POST", "/api/v1/admin/notifications", "发送系统通知", true,
                map("type", "system", "content", "通知内容"), map("success", true)),
            endpoint("DELETE", "/api/v1/admin/notifications/{id}", "删除通知", true, null, map("success", true))
        ));

        modules.add(module("Admin Sessions", "管理员-会话管理",
            endpoint("GET", "/api/v1/admin/sessions", "活跃会话列表（Redis）", true, null, null),
            endpoint("DELETE", "/api/v1/admin/sessions/{userId}", "撤销用户所有会话", true, null, map("success", true))
        ));

        modules.add(module("Admin Admins", "管理员-权限管理",
            endpoint("GET", "/api/v1/admin/admins", "管理员列表", true, null, null),
            endpoint("POST", "/api/v1/admin/admins", "提升为管理员", true,
                map("userId", 1), map("success", true)),
            endpoint("DELETE", "/api/v1/admin/admins/{id}", "降级为普通用户", true, null, map("success", true))
        ));

        modules.add(module("Admin Monitor", "管理员-系统监控",
            endpoint("GET", "/api/v1/admin/monitor/activities", "系统活动统计", true, null,
                map("totalUsers", 10, "totalPosts", 130, "totalComments", 78, "newUsersToday", 0, "newPostsToday", 0))
        ));

        modules.add(module("Admin Likes", "管理员-点赞数据",
            endpoint("GET", "/api/v1/admin/likes", "点赞数据概览（Redis CounterService）", true, null,
                map("message", "点赞数据存储在Redis中，通过CounterService管理"))
        ));

        modules.add(module("Admin API Docs", "API文档",
            endpoint("GET", "/api/v1/admin/api-docs", "本文档 — 所有API端点列表", false, null,
                map("modules", "...", "totalModules", 26))
        ));

        modules.add(module("Admin Audit", "管理员-审计日志",
            endpoint("GET", "/api/v1/admin/audit-logs", "登录审计日志", true, null,
                map("items", List.of(), "total", 142, "page", 1, "size", 20)),
            endpoint("GET", "/api/v1/admin/audit-logs/{id}", "审计日志详情", true, null, null)
        ));

        return map("modules", modules, "totalModules", modules.size());
    }

    // --- helpers (null-tolerant, no Map.of entry limit) ---

    /** Null-tolerant Map builder — map() rejects null values */
    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> map(Object... kv) {
        Map<K, V> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((K) kv[i], (V) kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> module(String name, String description, Map... endpoints) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("description", description);
        m.put("endpoints", Arrays.asList(endpoints));
        return m;
    }

    private static Map<String, Object> endpoint(String method, String path, String description,
                                                 boolean auth, Object request, Object response) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("method", method);
        e.put("path", path);
        e.put("description", description);
        e.put("auth", auth);
        if (request != null) e.put("request", request);
        if (response != null) e.put("response", response);
        return e;
    }
}
