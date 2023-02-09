#!/bin/bash

set -e

function create_deployment_group() {
  aws deploy create-deployment-group --application-name $CODEDEPLOY_APPLICATION_NAME \
    --deployment-group-name $CODEDEPLOY_DEPLOYMENTGROUP_NAME \
    --service-role-arn $CODEDEPLOY_SERVICE_ROLE_ARN \
    --deployment-config-name CodeDeployDefault.LambdaAllAtOnce \
    --deployment-style '{"deploymentType": "BLUE_GREEN", "deploymentOption": "WITH_TRAFFIC_CONTROL"}' \
    --auto-rollback-configuration '{"enabled": true}' \
    || true
}

create_deployment_group