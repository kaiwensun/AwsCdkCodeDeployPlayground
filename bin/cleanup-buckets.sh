#!/bin/bash

set -e

pushd $( dirname "${BASH_SOURCE[0]:-${(%):-%x}}" )/.. > /dev/null
source bin/utils.sh

SOFTWARE=httpd
REGION=`get_region`
ACCOUNT=`aws sts get-caller-identity --query Account --output text`

EC2_BUCKET_NAME="codedeploy-playground.revisions.${ACCOUNT}.${REGION}"

LAMBDA_BUCKET_NAME="codedeploy-playground.lambda.${ACCOUNT}.${REGION}"

LAMBDA_PIPELINE_BUCKET_NAME="CdkManaged-CodePipelineLambdaStack-${ACCOUNT}-${REGION}"
LAMBDA_PIPELINE_BUCKET_NAME="$(tr [A-Z] [a-z] <<< "${LAMBDA_PIPELINE_BUCKET_NAME}")"

buckets=(
  ${EC2_BUCKET_NAME}
  ${LAMBDA_BUCKET_NAME}
  ${LAMBDA_PIPELINE_BUCKET_NAME}
)

set +e

for bucket in ${buckets[@]}; do
#  aws s3 rm "s3://${bucket}" --recursive
  aws s3 rb "s3://${bucket}" --force 2>/dev/null
done
