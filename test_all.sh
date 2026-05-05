#!/bin/bash
# 知舟(ArkKnow) 全模块测试脚本
# 用法: bash test_all.sh

BASE="http://localhost:8080"
PASS=0
FAIL=0

check() {
  local name="$1"
  local method="$2"
  local url="$3"
  local data="$4"
  local token="$5"
  local expected="${6:-200}"

  if [ -n "$token" ]; then
    resp=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$BASE$url" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $token" \
      -d "$data")
  else
    resp=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$BASE$url" \
      -H "Content-Type: application/json" \
      -d "$data")
  fi

  if echo "$resp" | grep -q "$expected"; then
    echo "  ✅ $name (HTTP $resp)"
    PASS=$((PASS+1))
  else
    echo "  ❌ $name (expected $expected, got $resp)"
    FAIL=$((FAIL+1))
  fi
}

echo "============================================"
echo "  知舟(ArkKnow) 全模块接口测试"
echo "============================================"

# ── 认证模块 ──
echo ""
echo "── 认证系统 ──"

# 发送验证码
check "发送验证码" "POST" "/api/v1/auth/send-code" \
  '{"scene":"REGISTER","identifierType":"EMAIL","identifier":"test-e2e@arkknow.com"}'

# 从日志拿验证码
sleep 1
CODE=$(grep "LoggingCodeSender" /tmp/arknow.log 2>/dev/null | tail -1 | grep -oE "code=[0-9]+" | cut -d= -f2)
if [ -z "$CODE" ]; then CODE="000000"; fi

# 注册
REG=$(curl -s -X POST "$BASE/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"identifierType\":\"EMAIL\",\"identifier\":\"test-e2e@arkknow.com\",\"code\":\"$CODE\",\"password\":\"Test1234\",\"agreeTerms\":true}")
TOKEN=$(echo "$REG" | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',{}).get('accessToken',''))" 2>/dev/null)

if [ -n "$TOKEN" ]; then
  check "注册" "POST" "/api/v1/auth/register" \
    "{\"identifierType\":\"EMAIL\",\"identifier\":\"test-e2e@arkknow.com\",\"code\":\"$CODE\",\"password\":\"Test1234\",\"agreeTerms\":true}" "" "200"
else
  # 可能已经注册过，直接登录
  REG=$(curl -s -X POST "$BASE/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"identifierType":"EMAIL","identifier":"fresh@test.com","password":"Abc12345"}')
  TOKEN=$(echo "$REG" | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',{}).get('accessToken',''))" 2>/dev/null)
  check "密码登录" "POST" "/api/v1/auth/login" \
    '{"identifierType":"EMAIL","identifier":"fresh@test.com","password":"Abc12345"}' "" "200"
fi

check "密码登录" "POST" "/api/v1/auth/login" \
  '{"identifierType":"EMAIL","identifier":"fresh@test.com","password":"Abc12345"}' "" "200"

check "错误密码" "POST" "/api/v1/auth/login" \
  '{"identifierType":"EMAIL","identifier":"fresh@test.com","password":"wrong"}' "" "401"

check "查询用户" "GET" "/api/v1/auth/me" "" "$TOKEN" "200"

REFRESH=$(echo "$REG" | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',{}).get('refreshToken',''))" 2>/dev/null)
check "刷新令牌" "POST" "/api/v1/auth/token/refresh" \
  "{\"refreshToken\":\"$REFRESH\"}" "" "200"

# ── 用户资料 ──
echo ""
echo "── 用户资料 ──"
check "获取资料" "GET" "/api/v1/profile" "" "$TOKEN" "200"
check "更新资料" "PATCH" "/api/v1/profile" \
  '{"nickname":"E2E测试用户","bio":"全模块测试"}' "$TOKEN" "200"

# ── 知识帖文 ──
echo ""
echo "── 知识帖文 ──"

DRAFT=$(curl -s -X POST "$BASE/api/v1/knowposts/drafts" -H "Authorization: Bearer $TOKEN")
POST_ID=$(echo "$DRAFT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

check "创建草稿" "POST" "/api/v1/knowposts/drafts" "" "$TOKEN" "200"
check "更新元数据" "PATCH" "/api/v1/knowposts/$POST_ID" \
  "{\"title\":\"E2E测试文章\",\"tags\":\"[\\\"test\\\"]\"}" "$TOKEN" "204"
check "发布" "POST" "/api/v1/knowposts/$POST_ID/publish" "" "$TOKEN" "204"
check "置顶" "PATCH" "/api/v1/knowposts/$POST_ID/top" '{"isTop":true}' "$TOKEN" "204"
check "Feed(公开)" "GET" "/api/v1/knowposts/feed?page=1&size=5" "" "" "200"
check "详情" "GET" "/api/v1/knowposts/detail/$POST_ID" "" "" "200"

# ── 点赞/收藏 ──
echo ""
echo "── 计数系统 ──"
check "点赞" "POST" "/api/v1/action/like" \
  "{\"entityType\":\"knowpost\",\"entityId\":\"$POST_ID\"}" "$TOKEN" "200"
check "收藏" "POST" "/api/v1/action/fav" \
  "{\"entityType\":\"knowpost\",\"entityId\":\"$POST_ID\"}" "$TOKEN" "200"
check "查询计数" "GET" "/api/v1/counter/knowpost/$POST_ID?metrics=like,fav" "" "" "200"

# ── 用户关系 ──
echo ""
echo "── 用户关系 ──"
USER2=$(curl -s -X POST "$BASE/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"identifierType":"EMAIL","identifier":"user2@test.com","password":"Test1234"}' 2>/dev/null)
USER2_TOKEN=$(echo "$USER2" | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',{}).get('accessToken',''))" 2>/dev/null)

check "关注" "POST" "/api/v1/relation/follow?toUserId=3" "" "$TOKEN" "200"
check "查询关系" "GET" "/api/v1/relation/status?toUserId=3" "" "$TOKEN" "200"
check "关注列表" "GET" "/api/v1/relation/following?userId=3&limit=5" "" "$TOKEN" "200"
check "粉丝列表" "GET" "/api/v1/relation/followers?userId=3&limit=5" "" "$TOKEN" "200"

# ── OSS 存储 ──
echo ""
echo "── 对象存储 ──"
check "预签名URL" "POST" "/api/v1/storage/presign" \
  '{"scene":"knowpost_content","postId":"123","contentType":"text/markdown","ext":".md"}' "$TOKEN" "200"

# ── 搜索 ──
echo ""
echo "── 搜索系统 ──"
check "关键词搜索" "GET" "/api/v1/search?keyword=Java" "" "" "200"
check "前缀建议" "GET" "/api/v1/search/suggest?prefix=Spr" "" "" "200"

# ── 结果汇总 ──
echo ""
echo "============================================"
echo "  结果: $PASS 通过, $FAIL 失败"
echo "============================================"
