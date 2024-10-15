#!/bin/bash

set -e

STACK_NAME=ServerDeploymentStack

pushd $( dirname "${BASH_SOURCE[0]:-${(%):-%x}}" )/.. > /dev/null

source bin/utils.sh

stack_outputs=`aws cloudformation describe-stacks --stack-name $STACK_NAME --output json --query Stacks[0].Outputs`

function codedeploy() {
  aws deploy $@
}

CD_APPLICATION_NAME=`get_stack_output ApplicationName`
CD_DEPLOYMENTGROUP_NAME=`get_stack_output DeploymentGroupName`

deployment_id=`codedeploy list-deployments --application-name "${CD_APPLICATION_NAME}" --deployment-group-name "${CD_DEPLOYMENTGROUP_NAME}" --output text --query 'deployments[0]'`

echo Waiting for deployment $deployment_id

codedeploy wait deployment-successful --deployment-id "${deployment_id}" || true

deployment_info=`codedeploy get-deployment --deployment-id "${deployment_id}"`

# print out deployment info
jq '.' <<< "$deployment_info"
status=`jq -r '.deploymentInfo.status' <<< "${deployment_info}"`

deployment_target_id=`codedeploy list-deployment-targets --deployment-id $deployment_id --output text --query 'targetIds[0]'`

if [[ "${deployment_target_id}" == None ]]; then
  echo "No deployment targets found"
else
  codedeploy get-deployment-target --deployment-id $deployment_id --target-id $deployment_target_id
fi

echo "Deployment $deployment_id $status"

popd > /dev/null