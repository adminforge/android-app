#!/bin/bash

# Configuration
VERSION_PROPS="app/version.properties"
VERSION_NAME=$(grep "VERSION_NAME" $VERSION_PROPS | cut -d'=' -f2)
VERSION_CODE=$(grep "VERSION_CODE" $VERSION_PROPS | cut -d'=' -f2)
REPO_URL="https://git.adminforge.de/api/v1/repos/adminforge/android-app"
echo "Building release v${VERSION_NAME} (Code ${VERSION_CODE})..."

# 1. Extraction of Changelog
NOTES=$(awk "/## \[${VERSION_NAME}\]/{flag=1;next}/## \[/{flag=0}flag" CHANGELOG.md)

if [ -z "$NOTES" ]; then
    echo "Error: No notes found for v${VERSION_NAME} in CHANGELOG.md"
    exit 1
fi

# 2. Build APK
./gradlew assembleRelease

# 3. Rename and move APK
FINAL_APK="adminforge-v${VERSION_NAME}.apk"
cp app/build/outputs/apk/release/app-release.apk "$FINAL_APK"
cp "$FINAL_APK" adminforge-latest.apk

# 4. Update version.json
cat <<EOF > version.json
{
  "versionCode": $VERSION_CODE,
  "versionName": "$VERSION_NAME",
  "downloadUrl": "https://git.adminforge.de/adminforge/android-app/releases/download/v$VERSION_NAME/$FINAL_APK",
  "notes": "$VERSION_NAME Release\n\n$(echo "$NOTES" | sed ':a;N;$!ba;s/\n/\\n/g' | sed 's/"/\\\"/g')"
}
EOF

# 5. Git Tag and Push
git add app/version.properties CHANGELOG.md version.json
git commit -m "Build v$VERSION_NAME"
git tag -a "v$VERSION_NAME" -m "Release v$VERSION_NAME"
git push origin main
git push origin "v$VERSION_NAME"

echo "Build complete: $FINAL_APK"
# Deployment logic would go here
