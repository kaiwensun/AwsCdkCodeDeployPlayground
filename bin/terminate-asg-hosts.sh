#!/bin/bash

ASG_NAME=CdkManagedCodeDeployPlaygroundFleet

instanceIds=`aws autoscaling describe-auto-scaling-groups \
  --auto-scaling-group-names $ASG_NAME \
  --query AutoScalingGroups[0].Instances[*].InstanceId \
  --output text`

echo "current instance ids ${instanceIds}"
for instanceId in ${instanceIds}; do
  aws autoscaling terminate-instance-in-auto-scaling-group --instance-id $instanceId --no-should-decrement-desired-capacity
done

# aws autoscaling describe-auto-scaling-groups   --auto-scaling-group-names $ASG_NAME --query 'AutoScalingGroups[0].Instances[?LifecycleState == `InService` && HealthStatus == `Healthy`]'.InstanceId --output text | wc -l | xargs echo -n

