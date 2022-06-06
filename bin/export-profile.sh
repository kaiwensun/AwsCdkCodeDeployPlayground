unset ISENGARD_PRODUCTION_ACCOUNT
unset AWS_ACCESS_KEY_ID
unset AWS_SECRET_ACCESS_KEY
unset AWS_SESSION_TOKEN

profile=$1

if [ ! -z $profile ]; then
  export AWS_ACCESS_KEY_ID=`aws configure get aws_access_key_id --profile $profile`
  export AWS_SECRET_ACCESS_KEY=`aws configure get aws_secret_access_key --profile $profile`
  export AWS_REGION=`aws configure get region --profile $profile`
  export AWS_DEFAULT_REGION=$AWS_REGION

  echo $AWS_REGION
  aws sts get-caller-identity
fi