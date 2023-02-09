#!/bin/bash
set -e

echo "publish_lambda_function.sh is being executed by CodeBuild."

function publish_version() {
  version=$1
  res=`aws lambda get-function --function-name $LAMBDA_FUNCTION_NAME --qualifier $version` || true
  if  [[ -z "$res" ]]; then
    echo "PUBLISHING"
    if [[ 1 == "$version" ]]; then
      handler=lambda-A.lambda_handler
    else
      handler=lambda-B.lambda_handler
    fi
    aws lambda update-function-configuration --function-name $LAMBDA_FUNCTION_NAME --handler $handler
    sleep 10
    aws lambda publish-version --function-name $LAMBDA_FUNCTION_NAME
    sleep 10
  else
    echo "VERSION $res ALREADY EXISTS"
  fi
}

function correct_alias() {
  echo "BEFORE CORRECTION:"
  aws lambda get-alias --function-name $LAMBDA_FUNCTION_NAME --name $LAMBDA_FUNCTION_ALIAS

  version=`aws lambda get-alias --function-name $LAMBDA_FUNCTION_NAME --name $LAMBDA_FUNCTION_ALIAS --query FunctionVersion --output text`;

  if [[ 2 != "$version" ]]; then
    expected_version=1
  else
    expected_version=2
  fi
  aws lambda update-alias --function-name $LAMBDA_FUNCTION_NAME --name $LAMBDA_FUNCTION_ALIAS --function-version $expected_version --routing-config {}

  echo "AFTER CORRECTION:"
  aws lambda get-alias --function-name $LAMBDA_FUNCTION_NAME --name $LAMBDA_FUNCTION_ALIAS
}

publish_version 1
publish_version 2
correct_alias