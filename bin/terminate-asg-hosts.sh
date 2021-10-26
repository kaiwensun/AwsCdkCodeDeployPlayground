#!/bin/bash

ASG_NAME=CdkManagedCodeDeployPlaygroundFleet

aws deploy update-deployment-group \
  --application-name CdkManagedServerApplication \
  --current-deployment-group-name CdkManagedServerDeploymentGroup \
  --auto-scaling-groups

instanceIds=`aws autoscaling describe-auto-scaling-groups \
  --auto-scaling-group-names $ASG_NAME \
  --query AutoScalingGroups[0].Instances[*].InstanceId \
  --output text`

echo "current instance ids ${instanceIds}"
for instanceId in ${instanceIds}; do
  aws autoscaling terminate-instance-in-auto-scaling-group --instance-id $instanceId --no-should-decrement-desired-capacity
done

healthy=0
desired_healthy=1
while [[ $healthy -lt desired_healthy ]]; do
  echo "currently there are only $healthy healthy host(s). expecting $desired_healthy"
  healthy=$(aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names $ASG_NAME --query 'AutoScalingGroups[0].Instances[?LifecycleState == `InService` && HealthStatus == `Healthy`]'.InstanceId --output text | wc -l | xargs echo -n)
  sleep 5
done

echo "all hosts are provisioned."

aws deploy update-deployment-group \
  --application-name CdkManagedServerApplication \
  --current-deployment-group-name CdkManagedServerDeploymentGroup \
  --auto-scaling-groups $ASG_NAME
