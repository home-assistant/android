#!/bin/bash

set -ev

if [ "$TRAVIS_PULL_REQUEST" != "false" ]
then

    echo "Building PR"
    ./gradlew test
    ./gradlew lint
    ./gradlew ktlintCheck

elif [ -n "$TRAVIS_TAG" ]
then

    echo "Promoting Beta to Production"
    ./gradlew promoteArtifact --from-track beta --promote-track production

elif [ "$TRAVIS_BRANCH" = "master" ]
then

    echo "Building Master"
    mkdir -p app/src/main/play/release-notes/en-US/
    git log --format=%s $(git rev-list --tags --max-count=1)..HEAD > app/src/main/play/release-notes/en-US/default.txt

    export VERSION_CODE=`git rev-list --count HEAD`

    ./gradlew test
    ./gradlew lint
    ./gradlew ktlintCheck

    ./gradlew assembleRelease appDistributionUploadRelease publishReleaseBundle
fi
