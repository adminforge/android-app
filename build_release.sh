#!/bin/bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROPS="$DIR/app/version.properties"

if [ ! -f "$PROPS" ]; then
    echo "VERSION_CODE=100" > "$PROPS"
    echo "VERSION_NAME=1.0.0" >> "$PROPS"
fi

# Read properties
VERSION_CODE=$(grep "VERSION_CODE" "$PROPS" | cut -d'=' -f2)
VERSION_NAME=$(grep "VERSION_NAME" "$PROPS" | cut -d'=' -f2)

# Increment Code
NEW_CODE=$((VERSION_CODE + 1))

# Increment Patch Name (assumes x.y.z format)
IFS='.' read -ra PARTS <<< "$VERSION_NAME"
# If array length is 3, increment the third component
if [ ${#PARTS[@]} -eq 3 ]; then
    NEW_PATCH=$((PARTS[2] + 1))
    NEW_NAME="${PARTS[0]}.${PARTS[1]}.$NEW_PATCH"
else
    # Fallback if not x.y.z format
    NEW_NAME="${VERSION_NAME}-bumped"
fi

# Write back to file
echo "VERSION_CODE=$NEW_CODE" > "$PROPS"
echo "VERSION_NAME=$NEW_NAME" >> "$PROPS"

echo "Bumped App Version to $NEW_NAME (Version Code: $NEW_CODE)"

# Sync Changelog to assets
cp "$DIR/CHANGELOG.md" "$DIR/app/src/main/assets/CHANGELOG.md"

# Execute gradle build and rsync (handled by app/build.gradle.kts)
cd "$DIR"
./gradlew clean assembleRelease
