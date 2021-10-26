#!/bin/bash

set -e

SOFTWARE=httpd
REGION=`aws configure get region`
BUCKET_NAME="my-codedeploy.server-application.revisions.${REGION}"
CD_APPLICATION_NAME=CdkManagedServerApplication
CD_DEPLOYMENTGROUP_NAME=CdkManagedServerDeploymentGroup

set -e
pushd $( dirname "${BASH_SOURCE[0]}" )/.. > /dev/null

revisionS3Location="s3://${BUCKET_NAME}/${SOFTWARE}/${SOFTWARE}"
mkdir -p tmp

# pack latest revision
pushd "revisions"
pwd
zip -r "../tmp/${SOFTWARE}" "./${SOFTWARE}"
popd

# upload to S3
echo "Uploading to ${revisionS3Location}"
aws s3 cp "tmp/${SOFTWARE}.zip" "${revisionS3Location}"

# create deployment
revision='{
  "revisionType": "S3",
  "s3Location": {
    "bucket": "'"${BUCKET_NAME}"'",
    "key": "'"${SOFTWARE}/${SOFTWARE}"'",
    "bundleType": "zip"
  }
}'

aws deploy create-deployment \
  --application-name "${CD_APPLICATION_NAME}" \
  --deployment-group-name "${CD_DEPLOYMENTGROUP_NAME}" \
  --revision "${revision}"

popd > /dev/null