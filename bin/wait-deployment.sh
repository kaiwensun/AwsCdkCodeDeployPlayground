#!/bin/bash

set -e

STACK_NAME=ServerDeploymentStack

pushd $( dirname "${BASH_SOURCE[0]:-${(%):-%x}}" )/.. > /dev/null

source bin/utils.sh

stack_outputs=`aws cloudformation describe-stacks --stack-name $STACK_NAME --output json --query Stacks[0].Outputs`

CD_APPLICATION_NAME=`get_stack_output ApplicationName`
CD_DEPLOYMENTGROUP_NAME=`get_stack_output DeploymentGroupName`

deployment_id=`aws deploy list-deployments --application-name "${CD_APPLICATION_NAME}" --deployment-group-name "${CD_DEPLOYMENTGROUP_NAME}" --output text --query 'deployments[0]'`

aws deploy wait deployment-successful --deployment-id "${deployment_id}" || true

deployment_info=`aws deploy get-deployment --deployment-id "${deployment_id}"`

# print out deployment info
jq '.' <<< "$deployment_info"

deployment_target_id=`aws deploy list-deployment-targets --deployment-id $deployment_id --output text --query 'targetIds[0]'`

if [[ "${deployment_target_id}" == None ]]; then
  echo "No deployment targets found"
else
  aws deploy get-deployment-target --deployment-id $deployment_id --target-id $deployment_target_id
fi

popd > /dev/null