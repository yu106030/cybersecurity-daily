#!/bin/bash
# ============================================
# 网安日报速递 - 一键部署脚本
# 用法: bash deploy.sh [commit message]
# ============================================

set -e

PROJECT_DIR="C:/Users/UncleC/Desktop/cybersecurity-daily"
REPORTS_DIR="C:/Users/UncleC/WorkBuddy/automation-2026-05-13-task-1/reports"

cd "$PROJECT_DIR"

echo "==> [1/4] 复制最新HTML日报..."
# 查找reports目录中最新的HTML文件
latest_html=$(ls -t "$REPORTS_DIR"/*.html 2>/dev/null | head -1)

if [ -z "$latest_html" ]; then
    echo "    未找到HTML日报文件，跳过复制"
else
    filename=$(basename "$latest_html")
    # 从中文文件名提取日期并转为 YYYY-MM-DD 格式
    # 支持格式: 网安日报速递-2026年6月4日.html -> 2026-06-04.html
    # 使用python来处理中文日期转换
    converted=$("C:/Users/UncleC/.workbuddy/binaries/python/versions/3.13.12/python.exe" -c "
import re, sys
m = re.search(r'(\d{4})\u5e74(\d{1,2})\u6708(\d{1,2})\u65e5', '$filename')
if m:
    print(f'{m.group(1)}-{int(m.group(2)):02d}-{int(m.group(3)):02d}.html')
else:
    print('$filename')
" 2>/dev/null || echo "$filename")

    cp "$latest_html" "daily/$converted"
    echo "    复制: $filename -> daily/$converted"
fi

echo "==> [2/4] 检查Git状态..."
if [ ! -d ".git" ]; then
    echo "    Git仓库未初始化！请先运行:"
    echo "    cd $PROJECT_DIR && git init && git remote add origin <你的仓库URL>"
    exit 1
fi

changed=$(git status --porcelain | wc -l)
if [ "$changed" -eq 0 ]; then
    echo "    没有变更，无需提交"
    exit 0
fi

echo "==> [3/4] 提交变更..."
msg="${1:-daily update $(date +%Y-%m-%d)}"
git add -A
git commit -m "$msg"
echo "    提交: $msg"

echo "==> [4/4] 推送到远程..."
git push origin main
echo "    推送完成！"

echo ""
echo "✅ 部署成功！访问你的 GitHub Pages 查看更新"
