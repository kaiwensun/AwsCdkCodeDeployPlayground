#!/bin/bash

set -e

ASG_NAME='CdkManagedCodeDeployPlaygroundFleet'
instanceId=$(aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names $ASG_NAME --query 'AutoScalingGroups[0].Instances[?LifecycleState == `InService` && HealthStatus == `Healthy`]'.InstanceId --output text)
public_dns=`aws ec2 describe-instances --instance-ids $instanceId --query 'Reservations[*].Instances[*].PublicDnsName' --output text`
echo "ec2 dns: ${public_dns}"
region=`aws configure get region`
exec ssh -i ~/.ssh/${region}-ec2-keypair.pem ec2-user@${public_dns}
