package com.kaiwens.CodeDeployPlayground;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.codedeploy.EcsApplication;
import software.amazon.awscdk.services.codedeploy.IEcsApplication;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;

public class EcsServiceStack extends Stack {
    public EcsServiceStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public EcsServiceStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
        IVpc vpc = Vpc.Builder.create(this, "CdkManagedEcsVpc")
                .maxAzs(3)  // Default is all AZs in region
                .build();

        Cluster cluster = Cluster.Builder.create(this, "CdkManagedEcsCluster")
                .clusterName("CdkManagedEcsCluster")
                .vpc(vpc).build();

        ApplicationLoadBalancedFargateService fargate = ApplicationLoadBalancedFargateService.Builder
                .create(this, "CdkManagedFargateService")
                .serviceName("CdkManagedFargateService")
                .loadBalancerName("CdkManagedFargateServiceLB")
                .cluster(cluster)           // Required
                .cpu(512)                   // Default is 256
                .desiredCount(3)            // Default is 1
                .taskImageOptions(
                        ApplicationLoadBalancedTaskImageOptions.builder()
                                .image(ContainerImage.fromRegistry("amazon/amazon-ecs-sample"))
                                .build())
                .memoryLimitMiB(2048)       // Default is 512
                .publicLoadBalancer(true)   // Default is false
                .build();
        IEcsApplication codedeployApplication = EcsApplication.Builder.create(this, "CdkManagedEcsApplication")
                .applicationName("CdkManagedEcsApplication")
                .build();
    }
}
