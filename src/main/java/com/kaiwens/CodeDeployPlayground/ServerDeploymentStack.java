package com.kaiwens.CodeDeployPlayground;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CfnOutputProps;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.iam.CompositePrincipal;
import software.amazon.awssdk.utils.ImmutableMap;
import software.constructs.Construct;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.codedeploy.LoadBalancer;
import software.amazon.awscdk.services.codedeploy.ServerApplication;
import software.amazon.awscdk.services.codedeploy.ServerDeploymentConfig;
import software.amazon.awscdk.services.codedeploy.ServerDeploymentGroup;
import software.amazon.awscdk.services.ec2.AmazonLinuxGeneration;
import software.amazon.awscdk.services.ec2.AmazonLinuxImageProps;
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
import software.amazon.awscdk.services.elasticloadbalancing.LoadBalancerListener;
import software.amazon.awscdk.services.elasticloadbalancing.LoadBalancingProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddNetworkTargetsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseNetworkListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.NetworkListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.NetworkLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.NetworkTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.Protocol;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.CreateKeyPairRequest;
import software.amazon.awssdk.services.ec2.model.CreateKeyPairResponse;
import software.amazon.awssdk.services.ec2.model.DescribeKeyPairsRequest;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class ServerDeploymentStack extends Stack {

    public enum LBType {
        NONE,
        ALB,
        NLB,
        CLB
    }

    private final static InstanceType instanceType = InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.NANO);
    private final static String ASG_NAME = "CdkManagedCodeDeployPlaygroundFleet";

    public ServerDeploymentStack(final Construct scope, final String id) {
        this(scope, id, null, null);
    }

    public ServerDeploymentStack(final Construct scope, final String id, final StackProps props, LBType lbType) {
        super(scope, id, props);
        String region = props.getEnv().getRegion();
        String account = props.getEnv().getAccount();
        String keyPairName = String.format("%s-%s-ec2-keypair", account, region);
        createKeyPairIfAbsent(keyPairName);

        Role ec2InstanceProfileRole = Role.Builder.create(this, "InstanceProfile")
                .description("EC2 Instance Profile for CodeDeploy agent, managed by CDK")
                .assumedBy(ServicePrincipal.Builder.create("ec2.amazonaws.com").build())
                .managedPolicies(Arrays.asList(
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedEC2InstanceDefaultPolicy"),
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonEC2RoleforAWSCodeDeployLimited")))
                .build();

        UserData userData = UserData.forLinux();

        // install CodeDeploy agent
        userData.addCommands(
                "sudo yum update",
                "sudo yum install wget",

                // if don't care about ruby version, (may as old as ruby2.0)
                "sudo yum install ruby",
                // if need to install ruby2.6, uncomment below
                // "sudo yum install amazon-linux-extras",
                // "sudo amazon-linux-extras install ruby2.6",

                "# download codedeploy agent installer",
                "cd /home/ec2-user",
                String.format("wget https://aws-codedeploy-%s.s3.%s.amazonaws.com/latest/install", region, region),
                "chmod +x ./install",

                /* if want to install the latest version */
                "sudo ./install auto",

                /* else, if want to install a specific version */
                // "# erase any pre-installed codedeploy agent",
                // "sudo yum erase codedeploy-agent",
                // "# install specific version of codedeploy agent",
                // "sudo ./install auto -v releases/codedeploy-agent-1.3.1-1880.noarch.rpm",

                "# enable debug level log",
                "sudo sed -e 's/:verbose: false/:verbose: true/' /etc/codedeploy-agent/conf/codedeployagent.yml",
                "sudo service codedeploy-agent restart"
        );

        IVpc vpc = Vpc.fromLookup(this, "VPC", VpcLookupOptions.builder()
                .isDefault(true)
                .build());

        SecurityGroup securityGroup = SecurityGroup.Builder
                .create(this, "sg")
                .securityGroupName("SshAndWeb")
                .description(String.format("Managed by CDK stack %s. Open ports: SSH, HTTP, HTTPS", props.getStackName()))
                .vpc(vpc)
                .build();
        for (int port : new int[]{22, 80, 443}) {
            securityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(port));
        }

        AutoScalingGroup asg = AutoScalingGroup.Builder.create(this, "ASG")
                .autoScalingGroupName(ASG_NAME)
                .keyName(keyPairName)
                .instanceType(instanceType)
                .machineImage(MachineImage.latestAmazonLinux(
                        AmazonLinuxImageProps.builder()
                                .generation(AmazonLinuxGeneration.AMAZON_LINUX_2)
                                .build()
                ))
                .requireImdsv2(true)
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

        LoadBalancer lb = null;
        switch (lbType) {
            case NONE:
                break;
            case ALB:
                ApplicationLoadBalancer alb = ApplicationLoadBalancer.Builder.create(this, "ALB")
                        .vpc(vpc)
                        .loadBalancerName("CDKManagedALB")
                        .securityGroup(securityGroup)
                        .internetFacing(true)
                        .build();
                ApplicationListener albListener = alb.addListener("alb-listener", BaseApplicationListenerProps.builder()
                        .protocol(ApplicationProtocol.HTTP)
                        .build());
                ApplicationTargetGroup albTg = albListener.addTargets("alb-tg", AddApplicationTargetsProps.builder()
                        .protocol(ApplicationProtocol.HTTP)
                        .healthCheck(HealthCheck.builder()
                                .healthyHttpCodes("200")
                                .build())
                        .targetGroupName("CDKManagedAlbTg")
                        .targets(Collections.singletonList(asg))
                        .deregistrationDelay(Duration.seconds(10))
                        .build());
                lb = LoadBalancer.application(albTg);
                break;
            case NLB:
                NetworkLoadBalancer nlb = NetworkLoadBalancer.Builder.create(this, "NLB")
                        .vpc(vpc)
                        .internetFacing(true)
                        .loadBalancerName("CDKManagedNLB")
                        .build();
                NetworkListener nlbListener = nlb.addListener("nlb-listener", BaseNetworkListenerProps.builder()
                        .port(80)
                        .protocol(Protocol.TCP)
                        .build());
                NetworkTargetGroup nlbTg = nlbListener.addTargets("nlb-tg", AddNetworkTargetsProps.builder()
                        .port(80)
                        .protocol(Protocol.TCP)
                        .deregistrationDelay(Duration.seconds(10))
                        .healthCheck(HealthCheck.builder()
                                .build())
                        .targetGroupName("CDKManagedNlbTg")
                        .targets(Collections.singletonList(asg))
                        .build());
                lb = LoadBalancer.network(nlbTg);
                break;
            case CLB:
                software.amazon.awscdk.services.elasticloadbalancing.LoadBalancer clb = software.amazon.awscdk.services.elasticloadbalancing.LoadBalancer.Builder.create(this, "CLB")
                        .vpc(vpc)
                        .healthCheck(software.amazon.awscdk.services.elasticloadbalancing.HealthCheck.builder()
                                .port(80)
                                .protocol(LoadBalancingProtocol.HTTP)
                                .build())
                        .internetFacing(true)
                        .build();
                clb.getConnections().allowFromAnyIpv4(Port.tcpRange(80, 80));
                clb.addListener(LoadBalancerListener.builder()
                        .externalPort(80)
                        .externalProtocol(LoadBalancingProtocol.HTTP)
                        .build());
                clb.addTarget(asg);
                lb = LoadBalancer.classic(clb);
                break;
            default:
                throw new RuntimeException("Not implemented");

        }

        final String roleName = "CdkManagedCodeDeployServerDeploymentServiceRole-" + props.getEnv().getRegion();
        final List<String> SPs = Arrays.asList(
                "codedeploy.amazonaws.com"
        );
        Role codedeployServiceRole = Role.Builder.create(this, roleName)
                .roleName(roleName)
                .managedPolicies(Collections.singletonList(ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSCodeDeployRole")))
                .assumedBy(new CompositePrincipal(
//                        SPs.stream().map(ServicePrincipal::new).toArray(ServicePrincipal[]::new)
                        SPs.stream().map(sp -> ServicePrincipal.Builder.create(sp).conditions(ImmutableMap.of(
                                "StringEquals", ImmutableMap.of("aws:SourceAccount", account),
                                "StringLike", ImmutableMap.of("aws:SourceArn", String.format("arn:aws:codedeploy:%s:%s:deploymentgroup:%s/%s", region, account, applicationName, deploymentGroupName))
                        )).build()).toArray(ServicePrincipal[]::new)
                ))
                .build();
        ServerDeploymentGroup.Builder
                .create(this, deploymentGroupName)
                .deploymentGroupName(deploymentGroupName)
                .role(codedeployServiceRole)
                .application(application)
                .autoScalingGroups(Collections.singletonList(asg))
                .loadBalancer(lb)
                .deploymentConfig(ServerDeploymentConfig.ALL_AT_ONCE)
                .build();
        String bucketName = String.format("codedeploy-playground.revisions.%s.%s", account, region);
        Bucket.Builder.create(this, "RevisionBucket")
                .bucketName(bucketName)
                .versioned(true)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        new CfnOutput(this, "ApplicationName", CfnOutputProps.builder().value(applicationName).build());
        new CfnOutput(this, "DeploymentGroupName", CfnOutputProps.builder().value(deploymentGroupName).build());
        new CfnOutput(this, "S3BucketName", CfnOutputProps.builder().value(bucketName).build());
    }

    private void createKeyPairIfAbsent(final String keyPairName) {

        try (Ec2Client ec2 = Ec2Client.create()) {
            try {
                ec2.describeKeyPairs(DescribeKeyPairsRequest.builder()
                        .keyNames(keyPairName)
                        .build());
            } catch (Ec2Exception e) {
                if ("InvalidKeyPair.NotFound".equals(e.awsErrorDetails().errorCode())) {
                    String keyFilePath = String.format("%s/.ssh/%s.pem", System.getProperty("user.home"), keyPairName);
                    System.out.println(String.format("Creating ec2 keypair %s and putting the private key in %s", keyPairName, keyFilePath));
                    File keyFile = new File(keyFilePath);
                    try {
                        keyFile.createNewFile();
                        try (FileWriter keyFileWriter = new FileWriter(keyFilePath)) {
                            CreateKeyPairResponse createResp = ec2.createKeyPair(CreateKeyPairRequest.builder()
                                    .keyName(keyPairName)
                                    .build());

                            keyFileWriter.write(createResp.keyMaterial());
                        }
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    keyFile.setReadable(false, false);
                    keyFile.setReadable(true, true);
                    keyFile.setExecutable(false, false);
                    keyFile.setWritable(false, false);
                }
            }
        }
    }
}
