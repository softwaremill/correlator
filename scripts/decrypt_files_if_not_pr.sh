#!/usr/bin/env bash

if [[ "$TRAVIS_PULL_REQUEST" == "false" ]]; then
    openssl aes-256-cbc -K $encrypted_da1b9adf719b_key -iv $encrypted_da1b9adf719b_iv -in secrets.tar.enc -out secrets.tar -d
    tar xvf secrets.tar
fi
