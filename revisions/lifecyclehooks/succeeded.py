import boto3
import os

def lambda_handler(event, context):
    endpoint_url = os.getenv('CODEDEPLOY_ENDPOINT_URL')
    region_name = os.getenv('CODEDEPLOY_REGION_NAME')
    print(f"endpoint_url: {endpoint_url}")
    print(f"region: {region_name}")
    print(event)
    print(context)
    codedeploy = boto3.client('codedeploy', region_name=region_name, endpoint_url=endpoint_url)
    codedeploy.put_lifecycle_event_hook_execution_status(
        deploymentId=event["DeploymentId"],
        lifecycleEventHookExecutionId=event["LifecycleEventHookExecutionId"],
        status='Succeeded'
    )
