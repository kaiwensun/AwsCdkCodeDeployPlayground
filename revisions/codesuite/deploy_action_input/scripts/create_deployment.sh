#!/bin/bash

set -e

echo "starting create_deployment.sh"
aws sts get-caller-identity
compgen -A variable | grep CODEBUILD_SRC_DIR
compgen -A variable
echo "CODEBUILD_SRC_DIR=$CODEBUILD_SRC_DIR"
echo "CODEBUILD_SRC_DIR_BO_CDAppSpec=$CODEBUILD_SRC_DIR_BO_CDAppSpec"
echo "CODEBUILD_RESOLVED_SOURCE_VERSION=$CODEBUILD_RESOLVED_SOURCE_VERSION"
echo "CODEBUILD_SOURCE_REPO_URL=$CODEBUILD_SOURCE_REPO_URL"
echo "CODEBUILD_SOURCE_VERSION=$CODEBUILD_SOURCE_VERSION"
echo "CODEBUILD_SOURCE_REPO_URL_BO_CDAppSpec=$CODEBUILD_SOURCE_REPO_URL_BO_CDAppSpec"
echo "CODEBUILD_SRC_DIR_BO_CDAppSpec=$CODEBUILD_SRC_DIR_BO_CDAppSpec"
ls -la /codebuild/output
echo '----------'
pwd
ls -la
echo '========'
ls -la */

# eg. CODEBUILD_SOURCE_REPO_URL_BO_CDAppSpec=arn:aws:s3:::cdkmanaged-codepipelinelambdastack-513730896679-us-west-2/CDKManagedCodePipeli/BO_CDAppSp/9JBkqz2
bucket_and_key=`cut -d: -f6 <<< $CODEBUILD_SOURCE_REPO_URL_BO_CDAppSpec`
bucket=`cut -d/ -f1 <<< $bucket_and_key`
key=`cut -d/ -f2- <<< $bucket_and_key`

revision='{
  "revisionType": "S3",
  "s3Location": {
    "bucket": "'"${bucket}"'",
    "key": "'"${key}"'",
    "bundleType": "zip"
  }
}'
deployment_id=`aws deploy create-deployment \
  --application-name "${CODEDEPLOY_APPLICATION_NAME}" \
  --deployment-group-name "${CODEDEPLOY_DEPLOYMENTGROUP_NAME}" \
  --revision "${revision}" \
  --query deploymentId \
  --output text`

unset status
while [[ ! "$status" =~ ^(Succeeded|Failed|Stopped)$ ]]; do
  status=`aws deploy get-deployment --deployment-id $deployment_id --query deploymentInfo.status --output text`
  sleep 5
done