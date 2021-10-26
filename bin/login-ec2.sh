#/bin/bash

EC2_NAME='HelloCdkStack/ASG'
public_dns=`aws ec2 describe-instances --filters "Name=tag:Name,Values=${EC2_NAME}" --query Reservations[0].Instances[0].PublicDnsName --output text`
region=`aws configure get region`
ssh -i ~/.ssh/${region}-ec2-keypair.pem ec2-user@${public_dns}
