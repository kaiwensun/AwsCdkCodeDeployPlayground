package com.kaiwens.CodeDeployPlayground;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CfnOutputProps;
import software.amazon.awscdk.services.codedeploy.EcsBlueGreenDeploymentConfig;
import software.amazon.awscdk.services.codedeploy.EcsDeploymentGroup;
import software.amazon.awscdk.services.codedeploy.IEcsDeploymentGroup;
import software.amazon.awscdk.services.ecs.ContainerDefinition;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.DeploymentController;
import software.amazon.awscdk.services.ecs.DeploymentControllerType;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.TaskDefinition;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerAction;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetType;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codedeploy.EcsApplication;
import software.amazon.awscdk.services.codedeploy.IEcsApplication;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EcsFargateDeploymentStack extends Stack {
    public EcsFargateDeploymentStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public EcsFargateDeploymentStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
        IVpc vpc = Vpc.Builder.create(this, "CdkManagedEcsVpc")
                .maxAzs(3)  // Default is all AZs in region
                .build();

        List<TaskDefinition> taskDefinitions = Arrays.asList(
                createTaskDefinition("1", "amazon/amazon-ecs-sample"),
                createTaskDefinition("2", "nginxdemos/hello:latest"));

        Cluster cluster = Cluster.Builder.create(this, "CdkManagedEcsCluster")
                .clusterName("CdkManagedEcsCluster")
                .enableFargateCapacityProviders(true)
                .vpc(vpc).build();

        IApplicationTargetGroup tg1 = ApplicationTargetGroup.Builder.create(this, "CdkManagedEcsDeploymentTG1")
                .targetGroupName("CdkManagedEcsDeploymentTG1")
                .targetType(TargetType.IP)
                .port(80)
                .vpc(vpc)
                .build();
        IApplicationTargetGroup tg2 = ApplicationTargetGroup.Builder.create(this, "CdkManagedEcsDeploymentTG2")
                .targetGroupName("CdkManagedEcsDeploymentTG2")
                .targetType(TargetType.IP)
                .port(80)
                .vpc(vpc)
                .build();

//        IApplicationListener testListener = ApplicationListener.Builder;

        FargateService fargateService = FargateService.Builder.create(this, "Service")
                .serviceName("CdkManagedFargateService")
                .deploymentController(DeploymentController.builder()
                        .type(DeploymentControllerType.CODE_DEPLOY)
                        .build())
                .cluster(cluster)
                .taskDefinition(taskDefinitions.get(1))
                .build();

        ApplicationLoadBalancer alb = ApplicationLoadBalancer.Builder.create(this, "CdkManagedEcsDeploymentALB")
                .loadBalancerName("CdkManagedEcsDeploymentALB")
                .vpc(vpc)
                .internetFacing(true)
                .build();
        ApplicationListener listener = alb.addListener("Listener", BaseApplicationListenerProps.builder()
                .defaultAction(ListenerAction.forward(Collections.singletonList(tg1)))
                .port(80)
                .build());

        fargateService.attachToApplicationTargetGroup(tg1);

        IEcsApplication codedeployApplication = EcsApplication.Builder.create(this, "CdkManagedEcsApplication")
                .applicationName("CdkManagedEcsApplication")
                .build();
        IEcsDeploymentGroup deploymentGroup = EcsDeploymentGroup.Builder.create(this, "CdkManagedEcsDeploymentGroup")
                .deploymentGroupName("CdkManagedEcsDeploymentGroup")
                .application(codedeployApplication)
                .service(fargateService)
                .blueGreenDeploymentConfig(EcsBlueGreenDeploymentConfig.builder()
                        .blueTargetGroup(tg1)
                        .greenTargetGroup(tg2)
                        .listener(listener)
                        .build())
                .build();


        new CfnOutput(this, "DNS", CfnOutputProps.builder()
                .exportName("DNS")
                .value(alb.getLoadBalancerDnsName())
                .build());

        for (int i = 0; i < taskDefinitions.size(); i++) {
            int index = i + 1;
            TaskDefinition taskDefinition = taskDefinitions.get(i);
            new CfnOutput(this, "ContainerName-" + index, CfnOutputProps.builder()
                    .value(taskDefinition.getDefaultContainer().getContainerName())
                    .build());

            new CfnOutput(this, "ContainerPort-" + index, CfnOutputProps.builder()
                    .value(taskDefinition.getDefaultContainer().getContainerPort().toString())
                    .build());

            new CfnOutput(this, "Image-" + index, CfnOutputProps.builder()
                    .value(taskDefinition.getDefaultContainer().getImageName())
                    .build());

            new CfnOutput(this, "TaskDef-" + index, CfnOutputProps.builder()
                    .value(taskDefinition.getTaskDefinitionArn())
                    .build());
        }
    }

    private TaskDefinition createTaskDefinition(String index, String registryName) {
        FargateTaskDefinition fargateTaskDefinition = FargateTaskDefinition.Builder.create(this, "TaskDef" + index)
                .family("CdkManagedFargateTaskDefinition")
                .memoryLimitMiB(512)
                .cpu(256)
                .build();

        ContainerDefinition container = fargateTaskDefinition.addContainer("WebContainer" + index, ContainerDefinitionOptions.builder()
                // Use an image from DockerHub
                .image(ContainerImage.fromRegistry(registryName))
                .containerName("CdkManagedContainer")
                .build());
        container.addPortMappings(PortMapping.builder()
                .containerPort(80)
                .hostPort(80)
                .build());
        return fargateTaskDefinition;
    }
}
