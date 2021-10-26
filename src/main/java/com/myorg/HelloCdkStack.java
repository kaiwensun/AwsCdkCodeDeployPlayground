package com.myorg;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.codedeploy.ServerApplication;
import software.amazon.awscdk.services.codedeploy.ServerDeploymentGroup;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.MachineImage;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.UserData;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.s3.Bucket;

import java.util.Collections;


public class HelloCdkStack extends Stack {

    private final static InstanceType instanceType = InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.NANO);
    private final static String REGION = "us-east-1";
    private final static String KEY_PAIR_NAME = String.format("%s-ec2-keypair", REGION);
    private final static String ASG_NAME = "CdkManagedCodeDeployPlaygroundFleet";

    public HelloCdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public HelloCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Role ec2InstanceProfileRole = Role.Builder.create(this, "InstanceProfile")
                .description("EC2 Instance Profile for CodeDeploy agent, managed by CDK")
                .assumedBy(ServicePrincipal.Builder.create("ec2.amazonaws.com").build())
                .managedPolicies(Collections.singletonList(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonEC2RoleforAWSCodeDeploy")))
                .build();

        UserData userData = UserData.forLinux();

        // install CodeDeploy agent
        userData.addCommands(
                "sudo yum update",
                "sudo yum install ruby",
                "sudo yum install wget",

                "CODEDEPLOY_BIN=\"/opt/codedeploy-agent/bin/codedeploy-agent\"",
                "$CODEDEPLOY_BIN stop",
                "yum erase codedeploy-agent -y",

                "cd /home/ec2-user",
                String.format("wget https://aws-codedeploy-%s.s3.%s.amazonaws.com/latest/install", REGION, REGION),
                "chmod +x ./install",
                "sudo ./install auto"
        );

        IVpc vpc = Vpc.fromLookup(this, "VPC", VpcLookupOptions.builder()
                .isDefault(true)
                .build());

        SecurityGroup securityGroup = SecurityGroup.Builder
                .create(this, "sg")
                .securityGroupName("SshAndWeb")
                .description("Managed by CDK. Open ports: SSH, HTTP, HTTPS")
                .vpc(vpc)
                .build();
        for (int port : new int[]{22, 80, 443}) {
            securityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(port));
        }

        AutoScalingGroup asg = AutoScalingGroup.Builder.create(this, "ASG")
                .autoScalingGroupName(ASG_NAME)
                .keyName(KEY_PAIR_NAME)
                .instanceType(instanceType)
                .machineImage(MachineImage.latestAmazonLinux())
                .securityGroup(securityGroup)
                .role(ec2InstanceProfileRole)
                .userData(userData)
                .vpc(vpc)
                .build();
        final String applicationName = "CdkManagedServerApplication";
        final String deploymentGroupName = "CdkManagedServerDeploymentGroup";
        ServerApplication application = ServerApplication.Builder
                .create(this, applicationName)
                .applicationName(applicationName)
                .build();
        ServerDeploymentGroup deploymentGroup = ServerDeploymentGroup.Builder
                .create(this, deploymentGroupName)
                .deploymentGroupName(deploymentGroupName)
                .application(application)
                .autoScalingGroups(Collections.singletonList(asg))
                .build();
        String bucketName = String.format("my-codedeploy.server-application.revisions.%s", REGION);
        Bucket.Builder.create(this, "RevisionBucket")
                .bucketName(bucketName)
                .versioned(true)
                .build();
    }
}
