#!/bin/bash

set -e

function create_deployment_group() {
  unset found
  found=`aws deploy get-deployment-group --application-name $CODEDEPLOY_APPLICATION_NAME \
    --deployment-group-name $CODEDEPLOY_DEPLOYMENTGROUP_NAME` \
    || true
  if [ -z "$found" ]; then
    aws deploy create-deployment-group --application-name $CODEDEPLOY_APPLICATION_NAME \
      --deployment-group-name $CODEDEPLOY_DEPLOYMENTGROUP_NAME \
      --service-role-arn $CODEDEPLOY_SERVICE_ROLE_ARN \
      --deployment-config-name CodeDeployDefault.LambdaAllAtOnce \
      --deployment-style '{"deploymentType": "BLUE_GREEN", "deploymentOption": "WITH_TRAFFIC_CONTROL"}' \
      --auto-rollback-configuration '{"enabled": true, "events": ["DEPLOYMENT_FAILURE", "DEPLOYMENT_STOP_ON_ALARM", "DEPLOYMENT_STOP_ON_REQUEST"]}'
  fi
}

create_deployment_group