#!/bin/bash

set -e

STACK_NAME=LambdaDeploymentStack

stack_outputs=`aws cloudformation describe-stacks --stack-name $STACK_NAME --output json --query Stacks[0].Outputs`

function get_stack_output() {
  key=$1
  jq -r 'map(select(.OutputKey == "'"${key}"'")) | .[0].OutputValue' <<< $stack_outputs
}

CD_APPLICATION_NAME=`get_stack_output "ApplicationName"`
CD_DEPLOYMENTGROUP_NAME=`get_stack_output "DeploymentGroupName"`
FUNCTION_NAME=`get_stack_output "FunctionName"`
FUNCTION_ALIAS=`get_stack_output "FunctionAlias"`

# prepare lambda
versions=`aws lambda list-versions-by-function --function-name $FUNCTION_NAME --output json --query Versions`
versions=`jq -r 'map(select(.Version != "$LATEST"))' <<< $versions`
v1=`jq -r '.[0]' <<< $versions`
v2=`jq -r '.[1]' <<< $versions`
if [[ "$v1" == null ]]; then
  echo no Lambda versions found
  exit 1
fi
if [[ "$v2" == null ]]; then
  flavor1=`jq -r '.Description' <<< $v1`
  if [[ "$flavor1" == "A" ]]; then
    flavor2="B"
  else
    flavor2="A"
  fi
  s3_bucket=`get_stack_output "S3Bucket"`
  s3_key=`get_stack_output "S3Key"`
  code_sha256=`jq -r '.CodeSha256' <<< $v1`
  revision_id=`jq -r '.RevisionId' <<< $v1`
  aws lambda update-function-configuration --function-name $FUNCTION_NAME --description $flavor2 --handler "lambda-${flavor2}.lambda_handler"
  v2=`aws lambda publish-version --function-name $FUNCTION_NAME --code-sha256 $code_sha256`

  if [[ `jq -r '.Version' <<< "$v1"` == `jq -r '.Version' <<< "$v2"` ]]; then
    echo "Failed to publish new Lambda version"
    exit 1
  fi
fi

alias=`aws lambda get-alias --function-name $FUNCTION_NAME --name $FUNCTION_ALIAS`
from_v=`jq -r '.FunctionVersion' <<< $alias`
if [[ $from_v != `jq -r '.Version' <<< $v1` ]] && [[ $from_v != `jq -r '.Version' <<< $v2` ]]; then
  echo "Alias version is not one of `jq -r '.Version' <<< $v1` or `jq -r '.Version' <<< $v2`!"
  exit 1
fi
if [[ $from_v == `jq -r '.Version' <<< $v1` ]]; then
  to_v=`jq -r '.Version' <<< $v2`
else
  to_v=`jq -r '.Version' <<< $v1`
fi
routing_config=`jq -r '.RoutingConfig' <<< $alias`

if [[ "$routing_config" != null ]]; then
  echo "warn: force removing routing config"
  echo "$alias"
  aws lambda update-alias --function-name $FUNCTION_NAME --name $FUNCTION_ALIAS --routing-config '{}'
fi

echo "version: $from_v -> $to_v"

# generate AppSpecContent
appspec=$(jq '.' <<< '
{
 	"version": 0.0,
 	"Resources": [{
 		"myLambdaFunction": {
 			"Type": "AWS::Lambda::Function",
 			"Properties": {
 				"Name": "'$FUNCTION_NAME'",
 				"Alias": "'$FUNCTION_ALIAS'",
 				"CurrentVersion": "'$from_v'",
 				"TargetVersion": "'$to_v'"
 			}
 		}
 	}],
 	"Hooks": [
 	]
 }
')

# create deployment
revision='{
  "revisionType": "AppSpecContent",
  "appSpecContent": {
    "content": '`jq '. | tojson' <<< $appspec`'
  }
}'

echo "Current invoking result"
output=`aws lambda invoke --function-name $FUNCTION_NAME --qualifier $FUNCTION_ALIAS /dev/stdout`
echo "$output"

echo "Creating deployment"
deployment_id=`aws deploy create-deployment \
  --application-name "${CD_APPLICATION_NAME}" \
  --deployment-group-name "${CD_DEPLOYMENTGROUP_NAME}" \
  --revision "${revision}" \
  --output text`
echo "Deployment ID: $deployment_id"
echo "Invocation Command:"
echo "aws lambda invoke --function-name $FUNCTION_NAME --qualifier $FUNCTION_ALIAS /dev/stdout"
