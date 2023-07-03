#!/bin/bash

set -e

STACK_NAME=EcsFargateDeploymentStack

pushd $( dirname "${BASH_SOURCE[0]}" )/.. > /dev/null

source bin/utils.sh

stack_outputs=`aws cloudformation describe-stacks --stack-name $STACK_NAME --output json --query Stacks[0].Outputs`

CD_APPLICATION_NAME=`get_stack_output ApplicationName`
CD_DEPLOYMENTGROUP_NAME=`get_stack_output DeploymentGroupName`

dg_info=`aws deploy get-deployment-group --application-name $CD_APPLICATION_NAME --deployment-group-name $CD_DEPLOYMENTGROUP_NAME --output json --query deploymentGroupInfo`
dg_tg1=`jq -r '.loadBalancerInfo.targetGroupPairInfoList[0].targetGroups[0].name' <<< "$dg_info"`
dg_tg2=`jq -r '.loadBalancerInfo.targetGroupPairInfoList[0].targetGroups[1].name' <<< "$dg_info"`
ecs_service_name=`jq -r '.ecsServices[0].serviceName' <<< "$dg_info"`
ecs_cluster_name=`jq -r '.ecsServices[0].clusterName' <<< "$dg_info"`
ecs_service=`aws ecs describe-services --services $ecs_service_name --cluster $ecs_cluster_name --output json --query services[0]`

live_taskset=`jq -r '.taskSets | map(select(.status == "PRIMARY"))[0]' <<< "$ecs_service"`
live_taskdef=`jq -r '.taskDefinition' <<< "$live_taskset"`
live_image=
live_tg_arn=`jq -r '.loadBalancers[].targetGroupArn' <<< "$live_taskset"`
live_tg=`cut -d/ -f2 <<< $live_tg_arn`
container_name=`jq -r '.loadBalancers[].containerName' <<< "$live_taskset"`
container_port=`jq -r '.loadBalancers[].containerPort' <<< "$live_taskset"`

taskdef1=`get_stack_output "TaskDef1"`
taskdef2=`get_stack_output "TaskDef2"`

image1=`aws ecs describe-task-definition --task-definition $taskdef1 --query 'taskDefinition.containerDefinitions[].image' --output text`
image2=`aws ecs describe-task-definition --task-definition $taskdef2 --query 'taskDefinition.containerDefinitions[].image' --output text`

# validate taskdef and targetgroup
if [[ ! " $taskdef1 $taskdef2 " =~ "$live_taskdef" ]]; then
  echo TaskDefinition $live_taskdef from ECS service is not in CFN stack output
  exit 1
fi

if [[ ! " `get_stack_output "Image1"` `get_stack_output "Image2"` " =~ "$image1" ]]; then
  echo Image1 $image1 from ECS service is not in CFN stack output
  exit 1
fi

if [[ ! " `get_stack_output "Image1"` `get_stack_output "Image2"` " =~ "$image2" ]]; then
  echo Image2 $image2 from ECS service is not in CFN stack output
  exit 1
fi

if [[ ! $" $dg_tg1 $dg_tg2 " =~ "$live_tg" ]]; then
  echo TargetGroup $live_tg from CodeDeploy DeploymentGroup is not in CFN stack output
  exit 1
fi

# set target taskdef
if [[ "$taskdef1" == "$live_taskdef" ]]; then
  dest_taskdef="$taskdef2"
  live_image="$image1"
  dest_image="$image2"
else
  dest_taskdef="$taskdef1"
  live_image="$image2"
  dest_image="$image1"
fi

if [[ "$dg_tg1" == "$live_tg" ]]; then
  dest_tg="$dg_tg2"
else
  dest_tg="$dg_tg1"
fi

echo "TaskDef: ${live_taskdef} -> ${dest_taskdef}"
echo "TargetGroup: ${live_tg} -> ${dest_tg}"
echo "Image: ${live_image}  -> ${dest_image}"

# generate AppSpecContent
appspec=$(jq '.' <<< '
{
    "version": 0.0,
    "Resources": [
        {
            "TargetService": {
                "Type": "AWS::ECS::Service",
                "Properties": {
                    "TaskDefinition": "'${dest_taskdef}'",
                    "LoadBalancerInfo": {
                        "ContainerName": "'${container_name}'",
                        "ContainerPort": '${container_port}'
                    },
                    "PlatformVersion": "LATEST",
                    "NetworkConfiguration": {
                        "AwsvpcConfiguration": {
                            "Subnets": '"`jq '.networkConfiguration.awsvpcConfiguration.subnets' <<< "$ecs_service"`"',
                            "SecurityGroups": '"`jq '.networkConfiguration.awsvpcConfiguration.securityGroups' <<< "$ecs_service"`"',
                            "AssignPublicIp": '`jq '.networkConfiguration.awsvpcConfiguration.assignPublicIp' <<< "$ecs_service"`'
                        }
                    }
                }
            }
        }
    ],
    "Hooks": [
        {
            "BeforeInstall": "CDKManagedEcsDeploymentStackLifecycleHook"
        }
    ]
}
')
echo "AppSpecContent:"
echo "$appspec"

# create deployment
revision='{
  "revisionType": "AppSpecContent",
  "appSpecContent": {
    "content": '`jq '. | tojson' <<< $appspec`'
  }
}'

echo "DNS: `get_stack_output "DNS"`"

echo "Creating deployment"
aws deploy create-deployment \
  --application-name "${CD_APPLICATION_NAME}" \
  --deployment-group-name "${CD_DEPLOYMENTGROUP_NAME}" \
  --revision "${revision}" \
  --output text
