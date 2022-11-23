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
cdk deploy
```

### Create Deployment
```
./bin/create-deployment.sh
```

It will output deployment id. Log into AWS console and ssh into EC2 instance to monitor.

run `./bin/ssh-ec2/sh` can quickly ssh into the EC2 instance.

### Destroy the EC2 host and spin up a new one

```
./bin/terminate-asg-hosts.sh
```

This terminates the EC2 in the ASG, and waits a new EC2 instance to be launched by ASG and deployed by CodeDeploy. 

~~This requires a healthy last-successfully-deployed revision.~~

This temporarily detaches ASG hook from deployment group, so no deployment to new instance.

### Modify ELB type

Update the LBType passed into `ServerDeploymentStack`.

### Open web page hosted by the deployment target

Using the DNS of the access point.

```
./bin/open-web [ALB|NLB|TG|CLB|EC2]
```
The optional access point type defaults to `EC2`. 