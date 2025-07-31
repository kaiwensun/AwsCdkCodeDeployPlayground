#!/bin/bash

set -e

ASG_NAME='CdkManagedCodeDeployPlaygroundFleet'
instance_id=$(aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names $ASG_NAME --query 'AutoScalingGroups[0].Instances[?LifecycleState == `InService` && HealthStatus == `Healthy`]'.InstanceId --output text)

instance_resp=`aws ec2 describe-instances --instance-ids $instance_id --output json`
public_dns=`jq -r '.Reservations[0].Instances[0].PublicDnsName' <<< "$instance_resp"`
os_platform=`jq -r '.Reservations[0].Instances[0].Platform' <<< "$instance_resp"`
image_id=`jq -r '.Reservations[0].Instances[0].ImageId' <<< "$instance_resp"`
if [[ "${os_platform}" == windows ]]; then
  username=Administrator
else
  image_name=`aws ec2 describe-images --image-ids $image_id --query "Images[0].Name" --output text`
  if [[ $image_name == ubuntu/images/* ]]; then
    username=ubuntu
  else
    username=ec2-user
  fi
fi

echo "ec2 dns: ${public_dns}"
region=$AWS_DEFAULT_REGION
account=`aws sts get-caller-identity --query Account --output text`
exec ssh -i ~/.ssh/${account}-${region}-ec2-keypair.pem ${username}@${public_dns}
