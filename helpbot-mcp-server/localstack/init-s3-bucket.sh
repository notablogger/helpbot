#!/bin/bash
set -euo pipefail

awslocal s3 mb "s3://${HELPBOT_S3_BUCKET:-helpbot-documents}" || true
