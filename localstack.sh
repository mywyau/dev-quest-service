#!/usr/bin/env bash

docker run -d --name localstack -p 4566:4566 -e SERVICES=s3 localstack/localstack
