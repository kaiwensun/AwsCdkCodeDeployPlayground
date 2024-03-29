#!/bin/bash

set -e

SOFTWARE=httpd
REGION=${AWS_DEFAULT_REGION}
ACCOUNT=`aws sts get-caller-identity --query Account --output text`

STACK_NAME=ServerDeploymentStack

pushd $( dirname "${BASH_SOURCE[0]:-${(%):-%x}}" )/.. > /dev/null

source bin/utils.sh

stack_outputs=`aws cloudformation describe-stacks --stack-name $STACK_NAME --output json --query Stacks[0].Outputs`

BUCKET_NAME=`get_stack_output S3BucketName`
CD_APPLICATION_NAME=`get_stack_output ApplicationName`
CD_DEPLOYMENTGROUP_NAME=`get_stack_output DeploymentGroupName`

timestamp=`date +%Y-%m-%dT%H-%M-%S`
s3key=server/${SOFTWARE}/${SOFTWARE}-${timestamp}
revisionS3Location="s3://${BUCKET_NAME}/${s3key}"
mkdir -p tmp

# pack latest revision
pushd "revisions"
pwd
zip -r "../tmp/${SOFTWARE}" "./${SOFTWARE}"
popd

# upload to S3
echo "Uploading to ${revisionS3Location}"
aws s3 cp "tmp/${SOFTWARE}.zip" "${revisionS3Location}"
aws s3api put-object-tagging --bucket ${BUCKET_NAME} --key ${s3key} --tagging '{"TagSet": [{ "Key": "UseWithCodeDeploy", "Value": "true" }]}'

# create deployment
revision='{
  "revisionType": "S3",
  "s3Location": {
    "bucket": "'"${BUCKET_NAME}"'",
    "key": "'"${s3key}"'",
    "bundleType": "zip"
  }
}'

aws deploy create-deployment \
  --application-name "${CD_APPLICATION_NAME}" \
  --deployment-group-name "${CD_DEPLOYMENTGROUP_NAME}" \
  --revision "${revision}"

popd > /dev/null