#!/bin/bash
set -e

aws sts get-caller-identity
echo "generate_codedeloy_appspec.sh is being executed by CodeBuild. Enviornment variables include:"
echo "LAMBDA_FUNCTION_NAME: $LAMBDA_FUNCTION_NAME"
echo "LAMBDA_FUNCTION_ALIAS: $LAMBDA_FUNCTION_ALIAS"
echo "LAMBDA_FUNCTION_DESCRIPTION: $LAMBDA_FUNCTION_DESCRIPTION"



echo "SECONDARY_ARTIFACT_BUCKET_NAME: $SECONDARY_ARTIFACT_BUCKET_NAME"
echo "SECONDARY_ARTIFACT_NAME: $SECONDARY_ARTIFACT_NAME"
echo "SECONDARY_ARTIFACT_OBJECT_KEY: $SECONDARY_ARTIFACT_OBJECT_KEY"
echo "SECONDARY_ARTIFACT_S3_LOCATION: $SECONDARY_ARTIFACT_S3_LOCATION"


function get_alias_version() {
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

from_v=`aws lambda get-alias --function-name $LAMBDA_FUNCTION_NAME --name $LAMBDA_FUNCTION_ALIAS --query FunctionVersion --output text`;
if [[ 2 != "$from_v" ]]; then
  to_v=2
else
  to_v=1
fi

appspec=$(jq '.' <<< '
{
 	"version": 0.0,
 	"Resources": [{
 		"myLambdaFunction": {
 			"Type": "AWS::Lambda::Function",
 			"Properties": {
 				"Name": "'$LAMBDA_FUNCTION_NAME'",
 				"Alias": "'$LAMBDA_FUNCTION_ALIAS'",
 				"CurrentVersion": "'$from_v'",
 				"TargetVersion": "'$to_v'"
 			}
 		}
 	}],
 	"Hooks": [
 	]
 }
')

echo "$appspec"

mkdir -p "$APPSPEC_FOLDER"
echo "$appspec" > "$APPSPEC_FOLDER/$APPSPEC_FILE_NAME"
# do not explicitly zip. CodeBuild will zip artifacts
#zip -r "$APPSPEC_FOLDER/$APPSPEC_FILE_NAME" "appspec.json"
