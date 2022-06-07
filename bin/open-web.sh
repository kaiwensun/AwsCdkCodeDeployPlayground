#!/bin/bash

set -e

ASG_NAME=CdkManagedCodeDeployPlaygroundFleet

LB_TYPE=$1

function ec2_url() {
  instanceId=$(aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names $ASG_NAME --query 'AutoScalingGroups[0].Instances[?LifecycleState == `InService` && HealthStatus == `Healthy`]'.InstanceId --output text)
  DNS=`ec2 describe-instances --instance-ids $instanceId --query Reservations[0].Instances[0].PublicDnsName --output text`
  echo "$DNS"
}

function elbv2_url() {
  tgArn=`aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names $ASG_NAME --query AutoScalingGroups[0].TargetGroupARNs[0] --output text`
  lbArn=`aws elbv2 describe-target-groups --target-group-arns $tgArn --query TargetGroups[0].LoadBalancerArns[0] --output text`
  DNS=`aws elbv2 describe-load-balancers --load-balancer-arns $lbArn --query LoadBalancers[0].DNSName --output text`
  echo "$DNS"
}

case $LB_TYPE in
  ALB|NLB|TG)
    url=`elbv2_url`
  ;;
  CLB)
  ;;
  *)
    url=`ec2_url`
    ;;
esac

open "http://${url}"
