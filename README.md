### Prerequisites
* Python3, Java, Maven
* cdk v2 (`npm install -g aws-cdk`)
* Create an EC2 key pair named `us-east-1-ec2-keypair`, and put the `us-east-1-ec2-keypair.pem` ssh key pair in ~/.ssh locally (or use whatever region you are going to use)

### Setup environment variables
```
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...

export AWS_REGION="us-east-1"  # doesn't have to be us-east-1
export AWS_DEFAULT_REGION=$AWS_REGION
```
or if you would like to use a profile in your `~/.aws/config`
```
source ./bin/export-profile.sh <cli profile name>
```

### Setup infra
(The bootstrap needs to run only once. No need to run again in the future.)
```
ACCOUNT_ID=`aws sts get-caller-identity --query Account --output text`
cdk bootstrap "aws://$ACCOUNT_ID/$AWS_REGION"
```
after bootstrap or local code change, then create/update infra by running:
```
cdk deploy <stack name>
```

To find available stack names, check `CdkApp.java`, or run:
```
cdk list
```

### EC2 Server deployment

Resources are created by `ServerDeploymentStack`

#### Create Deployment
```
./bin/create-deployment.sh
```

It will output deployment id. Log into AWS console and ssh into EC2 instance to monitor.

run `./bin/ssh-ec2/sh` can quickly ssh into the EC2 instance.

#### Destroy the EC2 host and spin up a new one

```
./bin/terminate-asg-hosts.sh
```

This terminates the EC2 in the ASG, and waits a new EC2 instance to be launched by ASG and deployed by CodeDeploy. 

~~This requires a healthy last-successfully-deployed revision.~~

This temporarily detaches ASG hook from deployment group, so no deployment to new instance.

#### Modify ELB type

Update the LBType passed into `ServerDeploymentStack`.

#### Open web page hosted by the deployment target

Using the DNS of the access point.

```
./bin/open-web [ALB|NLB|TG|CLB|EC2]
```
The optional access point type defaults to `EC2`. 

### ECS deployment

Resources are created by `EcsFargateDeploymentStack`

#### Create Deployment

```
./bin/create-ecs-deployment.sh
```

Assuming all related resources (CFN, ECS, ELB, CodeDeploy) are in a good state, this script automatically detects the original and replacement target group and task definition, generates AppSpecContent, and create deploy the deployment.

You can use the DNS printed by this script (from CFN stack output) to see a new web page is deployed.

### Lambda deployment

Resources are created by `LambdaDeploymentStack`

### Create Deployment

```
./bin/create-lambda-deployment.sh
```

This script automatically detects which Lambda function version is the current version of the alias, and creates a deployment to shift traffic to another version.

CDK/CFN does not support publishing multiple versions for the same function in one single stack creation/update. So this script publishes a second version if there is only one.

The code of the two lambda versions is in `revisions/lambda_app/` folder. To differentiate the two versions, the code prints different "flavors", A or B.

### CloudFormation ECS Blue/Green Deployment

This CDK stack models the CFN-ECS B/G deployment mentioned by [the doc here](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/blue-green.html). Please read this doc completely to understand the limitations, and when a CodeDeploy deployment will be created.

The stack takes a parameter `DockerImage`. The initial stack creation creates only the blue resources, so there is not any CodeDeploy deployment. Then you can change the image to trigger a B/G deployment.

```
cdk deploy EcsFargateBGHookStack --parameters DockerImage='amazon/amazon-ecs-sample'
```

```
cdk deploy EcsFargateBGHookStack --parameters DockerImage='nginxdemos/hello:latest'
```

### Pipeline Stack

Prerequisite: install git-remote-codecommit - https://github.com/aws/git-remote-codecommitcodecommit::us-west-2://CdkManagedRepository

git clone codecommit::us-west-2://CdkManagedRepository
mv CdkManagedRepository/.git .

needs to push a commit to add execution permission to the file permission mode 

TODO