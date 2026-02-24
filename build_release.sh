#!/bin/bash
set -e

# Configuration
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != "main" ]; then
    echo "Error: Release must be built from the 'main' branch."
    exit 1
fi

TOKEN=$(grep "ADMINFORGE_GIT_TOKEN" local.properties | cut -d'=' -f2)
VERSION_NAME=$(grep "VERSION_NAME" app/version.properties | cut -d'=' -f2)
VERSION_CODE=$(grep "VERSION_CODE" app/version.properties | cut -d'=' -f2)
REPO_URL="https://git.adminforge.de/api/v1/repos/adminforge/android-app"
echo "Building release v${VERSION_NAME} (Code ${VERSION_CODE})..."

# 1. Extraction of Changelog
NOTES=$(awk "/## \[${VERSION_NAME}\]/{flag=1;next}/## \[/{flag=0}flag" CHANGELOG.md)

if [ -z "$NOTES" ]; then
    echo "Error: No notes found for v${VERSION_NAME} in CHANGELOG.md"
    exit 1
fi

# 2. Build APK
./gradlew clean assembleRelease

# 3. APK Path resolution
APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
    APK_PATH=$(find app/build/outputs/apk/release -name "*.apk" | head -n 1)
fi

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
    echo "Error: APK not found after build!"
    exit 1
fi

# 4. Rename APK for Gitea
FINAL_APK="adminforge-v${VERSION_NAME}.apk"
cp "$APK_PATH" "$FINAL_APK"

# 4. Update version.json
cat <<EOF > version.json
{
  "versionCode": $VERSION_CODE,
  "versionName": "$VERSION_NAME",
  "downloadUrl": "https://git.adminforge.de/adminforge/android-app/releases/download/v$VERSION_NAME/$FINAL_APK",
  "notes": "$VERSION_NAME Release\n\n$(echo "$NOTES" | sed ':a;N;$!ba;s/\n/\\n/g' | sed 's/"/\\"/g')"
}
EOF

# 5. Git Commit & Tag
git add .
git commit -m "Release v${VERSION_NAME}" || echo "No changes to commit"
git push origin main
git tag -a "v${VERSION_NAME}" -m "Release v${VERSION_NAME}" --force
git push origin "v${VERSION_NAME}" --force

echo "Waiting for tag propagation..."
sleep 5

# 6. Gitea Release
echo "Creating Gitea release..."
RELEASE_DATA=$(cat <<EOF
{
  "tag_name": "v${VERSION_NAME}",
  "target_commitish": "main",
  "name": "v${VERSION_NAME}",
  "body": "$(echo "$NOTES" | sed ':a;N;$!ba;s/\n/\\n/g' | sed 's/"/\\"/g')",
  "draft": false,
  "prerelease": false
}
EOF
)

RESPONSE=$(curl -s -X POST "$REPO_URL/releases" \
    -H "Authorization: token $TOKEN" \
    -H "Content-Type: application/json" \
    -d "$RELEASE_DATA")

RELEASE_ID=$(echo "$RESPONSE" | grep -oP '"id":\s*\K\d+' | head -n 1)

if [ -z "$RELEASE_ID" ]; then
    RELEASE_ID=$(curl -s -H "Authorization: token $TOKEN" "$REPO_URL/releases" | grep -oP '"tag_name": "v'${VERSION_NAME}'"[^}]*"id": \K\d+' | head -n 1)
fi

if [ -z "$RELEASE_ID" ]; then
    echo "Error creating or finding release: $RESPONSE"
    exit 1
fi

echo "Uploading APK as $FINAL_APK..."
curl -X POST "$REPO_URL/releases/$RELEASE_ID/assets" \
    -H "Authorization: token $TOKEN" \
    -H "Content-Type: multipart/form-data" \
    -F "attachment=@$FINAL_APK"

rm "$FINAL_APK"

echo "Release v${VERSION_NAME} successful!"
