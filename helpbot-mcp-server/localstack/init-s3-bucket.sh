#!/bin/bash
set -euo pipefail

BUCKET="${HELPBOT_S3_BUCKET:-helpbot-documents}"

awslocal s3 mb "s3://${BUCKET}" || true

# Upload seed documents preserving folder structure (internal/ and public/)
if [ -d /seed-documents ]; then
  awslocal s3 cp /seed-documents "s3://${BUCKET}/" --recursive
  echo "Uploaded seed documents to s3://${BUCKET}"
  awslocal s3 ls "s3://${BUCKET}/" --recursive
fi
