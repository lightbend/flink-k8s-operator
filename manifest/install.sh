#!/bin/bash

BUNDLE_DIR=olm/crd

operator-courier verify $BUNDLE_DIR

# $ AUTH_TOKEN=$(curl -sH "Content-Type: application/json" -XPOST https://quay.io/cnr/api/v1/users/login -d '
# > {
# >     "user": {
# >         "username": "'"blublinsky"'",
# >         "password": "'"Boris1954"'"
# >     }
# > }' | jq -r '.token')
AUTH_TOKEN="basic Ymx1YmxpbnNreTpCb3JpczE5NTQ="

EXAMPLE_NAMESPACE=blublinsky
EXAMPLE_REPOSITORY=lightbend-flink
EXAMPLE_RELEASE=0.0.3
operator-courier push $BUNDLE_DIR $EXAMPLE_NAMESPACE $EXAMPLE_REPOSITORY \
$EXAMPLE_RELEASE "$AUTH_TOKEN"