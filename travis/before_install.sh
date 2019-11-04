#!/bin/bash

if [ "$TRAVIS_PULL_REQUEST" = "false" ]
then
    openssl aes-256-cbc -K $encrypted_5bab0fc19e76_key -iv $encrypted_5bab0fc19e76_iv -in secrets.tar.enc -out secrets.tar -d
    tar xvf travis/secrets.tar
    cp travis/google-services.json app/google-services.json
    cp travis/upload_keystore.keystore release_keystore.keystore
    cp travis/home-assistant-mobile-apps-5fd6b9dd0fdb.json firebaseAppDistributionServiceCredentialsFile.json
    cp travis/home-assistant-mobile-apps-0b13292f44c4.json playStorePublishServiceCredentialsFile.json
fi