package com.kaiwens.CodeDeployPlayground;

import software.amazon.awscdk.CfnCodeDeployBlueGreenAdditionalOptions;
import software.amazon.awscdk.CfnCodeDeployBlueGreenApplication;
import software.amazon.awscdk.CfnCodeDeployBlueGreenApplicationTarget;
import software.amazon.awscdk.CfnCodeDeployBlueGreenEcsAttributes;
import software.amazon.awscdk.CfnCodeDeployBlueGreenHook;
import software.amazon.awscdk.CfnCodeDeployBlueGreenLifecycleEventHooks;
import software.amazon.awscdk.CfnElement;
import software.amazon.awscdk.CfnParameter;
import software.amazon.awscdk.CfnTrafficRoute;
import software.amazon.awscdk.CfnTrafficRouting;
import software.amazon.awscdk.CfnTrafficRoutingConfig;
import software.amazon.awscdk.CfnTrafficRoutingType;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.cloudassembly.schema.LoadBalancerType;
import software.amazon.awscdk.services.ec2.CfnSecurityGroup;
import software.amazon.awscdk.services.ec2.ISubnet;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.amazon.awscdk.services.ecs.CfnCluster;
import software.amazon.awscdk.services.ecs.CfnPrimaryTaskSet;
import software.amazon.awscdk.services.ecs.CfnService;
import software.amazon.awscdk.services.ecs.CfnTaskDefinition;
import software.amazon.awscdk.services.ecs.CfnTaskSet;
import software.amazon.awscdk.services.ecs.DeploymentControllerType;
import software.amazon.awscdk.services.ecs.LaunchType;
import software.amazon.awscdk.services.ecs.NetworkMode;
import software.amazon.awscdk.services.elasticloadbalancingv2.CfnListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.CfnLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.CfnTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.IpAddressType;
import software.amazon.awscdk.services.elasticloadbalancingv2.Protocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetType;
import software.amazon.awscdk.services.iam.CompositePrincipal;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.IFunction;
import software.amazon.awscdk.services.lambda.Runtime;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EcsFargateBGHookStack extends Stack {
    public EcsFargateBGHookStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public EcsFargateBGHookStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        CfnParameter dockerImageParameter = CfnParameter.Builder.create(this, "DockerImage")
                .type("String")
                .description("DockerImage. Used to make a template change to update stack and trigger a new deployment")
                .allowedValues(Arrays.asList("amazon/amazon-ecs-sample", "nginxdemos/hello:latest"))
                .build();

        final String registryName = dockerImageParameter.getValueAsString();

        this.getTemplateOptions().setTransforms(Arrays.asList("AWS::CodeDeployBlueGreen"));


        final int PROD_PORT = 80;
        final int TEST_PORT = 8080;
        IFunction hookLambdaFunction = createLambdaLifecycleHookFunction("succeeded.lambda_handler");

        IVpc vpc = Vpc.fromLookup(this, "Vpc", VpcLookupOptions.builder()
                .isDefault(true)
                .build());
        List<ISubnet> subnets = vpc.getPublicSubnets().subList(0, 2);

        CfnSecurityGroup sg = CfnSecurityGroup.Builder.create(this, "CFNTestSecurityGroup")
                .groupName(prefixName("SecurityGroup"))
                .groupDescription(prefixName("SecurityGroup"))
                .vpcId(vpc.getVpcId())
                .securityGroupIngress(Arrays.asList(PROD_PORT, TEST_PORT)
                        .stream().map(port -> CfnSecurityGroup.IngressProperty.builder()
                                .ipProtocol(Protocol.TCP.name().toLowerCase())
                                .fromPort(port)
                                .toPort(port)
                                // allow public access
                                .cidrIp(port == PROD_PORT ? "0.0.0.0/0" : vpc.getVpcCidrBlock()).build())
                        .collect(Collectors.toList()))
                .build();


        CfnTargetGroup blueTG = createCfnTargetGroup("BlueTG", PROD_PORT, vpc);
        CfnTargetGroup greenTG = createCfnTargetGroup("GreenTG", PROD_PORT, vpc);

        CfnLoadBalancer alb = CfnLoadBalancer.Builder.create(this, "ALB")
                .name(prefixName("ALB"))
                .scheme("internet-facing")
                .securityGroups(Arrays.asList(sg.getAttrGroupId()))
                .subnets(subnets.stream().map(n -> n.getSubnetId()).collect(Collectors.toList()))
                .type(LoadBalancerType.APPLICATION.name().toLowerCase())
                .ipAddressType(IpAddressType.IPV4.name().toLowerCase())
                .build();

        CfnListener prodListener = createCfnListener("ProdListener", alb, PROD_PORT, blueTG);
        CfnListener testListener = createCfnListener("TestListener", alb, TEST_PORT, blueTG);

        Role ecsTaskExecutionRole = Role.Builder.create(this, "EcsTaskExecutionRole")
                .roleName(prefixName("EcsTaskExecutionRole-" + getRegion()))
                .assumedBy(ServicePrincipal.Builder.create("ecs-tasks").build())
                .managedPolicies(Collections.singletonList(ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")))
                .build();

        final String containerName = prefixName("Container");
        CfnTaskDefinition taskDefA = CfnTaskDefinition.Builder.create(this, "TaskDefinitionA")
                .executionRoleArn(ecsTaskExecutionRole.getRoleArn())
                .containerDefinitions(Arrays.asList(CfnTaskDefinition.ContainerDefinitionProperty.builder()
                        .name(containerName)
                        .image(registryName)
                        .essential(true)
                        .portMappings(Arrays.asList(CfnTaskDefinition.PortMappingProperty.builder()
                                .hostPort(PROD_PORT)
                                .containerPort(PROD_PORT)
                                .protocol(software.amazon.awscdk.services.ecs.Protocol.TCP.name().toLowerCase())
                                .build()))
                        .build()))
                .requiresCompatibilities(Arrays.asList(LaunchType.FARGATE.name()))
                .networkMode(NetworkMode.AWS_VPC.name().toLowerCase().replace("_", ""))
                .cpu("256")
                .memory("512")
                .build();

        CfnCluster cluster = CfnCluster.Builder.create(this, "Cluster").build();
        CfnService service = CfnService.Builder.create(this, "Service")
                .cluster(cluster.getAttrArn())
                .desiredCount(1)
                .deploymentController(CfnService.DeploymentControllerProperty.builder()
                        .type(DeploymentControllerType.EXTERNAL.name())
                        .build())
                .build();

        CfnTaskSet taskSetA = CfnTaskSet.Builder.create(this, "TaskSetA")
                .cluster(cluster.getAttrArn())
                .service(service.getAttrServiceArn())
                .taskDefinition(taskDefA.getAttrTaskDefinitionArn())
                .launchType(LaunchType.FARGATE.name())
                .networkConfiguration(CfnTaskSet.NetworkConfigurationProperty.builder()
                        .awsVpcConfiguration(CfnTaskSet.AwsVpcConfigurationProperty.builder()
                                .assignPublicIp("ENABLED")
                                .securityGroups(Arrays.asList(sg.getAttrGroupId()))
                                .subnets(subnets.stream().map(n -> n.getSubnetId()).collect(Collectors.toList()))
                                .build())
                        .build())
                .scale(CfnTaskSet.ScaleProperty.builder()
                        .unit("PERCENT")
                        .value(1.0)
                        .build())
                .loadBalancers(Arrays.asList(CfnTaskSet.LoadBalancerProperty.builder()
                        .containerName(containerName)
                        .containerPort(PROD_PORT)
                        .targetGroupArn(blueTG.getAttrTargetGroupArn())
                        .build()))
                .build();

        CfnPrimaryTaskSet primaryTaskSet = CfnPrimaryTaskSet.Builder.create(this, "PrimaryTaskSet")
                .cluster(cluster.getAttrArn())
                .service(service.getAttrServiceArn())
                .taskSetId(taskSetA.getAttrId())
                .build();

        final String roleName = prefixName("DeploymentServiceRole-" + getRegion());
        final List<String> SPs = Arrays.asList(
                "codedeploy.amazonaws.com"
        );
        Role codedeployServiceRole = Role.Builder.create(this, roleName)
                .roleName(roleName)
                .managedPolicies(Collections.singletonList(ManagedPolicy.fromAwsManagedPolicyName("AWSCodeDeployRoleForECS")))
                .assumedBy(new CompositePrincipal(
                        SPs.stream().map(ServicePrincipal::new).toArray(ServicePrincipal[]::new)
                ))
                .build();

        CfnCodeDeployBlueGreenHook hooks = CfnCodeDeployBlueGreenHook.Builder.create(this, "CodeDeployBlueGreenHook")
                .trafficRoutingConfig(CfnTrafficRoutingConfig.builder()
                        .type(CfnTrafficRoutingType.ALL_AT_ONCE)
                        .build())
                .additionalOptions(CfnCodeDeployBlueGreenAdditionalOptions.builder()
                        .terminationWaitTimeInMinutes(1)
                        .build())
                .lifecycleEventHooks(CfnCodeDeployBlueGreenLifecycleEventHooks.builder()
                        // Can't use hookLambdaFunction.getFunctionName(). Otherwise, will see error:
                        // Template format error: Intrinsic functions in the Hooks block must only contain parameter values, stack metadata or mappings
                        .beforeAllowTraffic(prefixName("LifecycleHook"))
                        .build())
                // Can't use codedeployServiceRole.getRoleName(). Otherwise, will see error:
                // Template format error: Intrinsic functions in the Hooks block must only contain parameter values, stack metadata or mappings
                .serviceRole(roleName)
                .applications(Arrays.asList(CfnCodeDeployBlueGreenApplication.builder()
                        .target(CfnCodeDeployBlueGreenApplicationTarget.builder()
                                .type(CfnService.CFN_RESOURCE_TYPE_NAME)
                                .logicalId(service.getLogicalId())
                                .build())
                        .ecsAttributes(CfnCodeDeployBlueGreenEcsAttributes.builder()
                                .taskDefinitions(Arrays.asList(taskDefA.getLogicalId(), "TaskDefinitionB"))
                                // WARN: probably need two task sets here. see public doc CFN example
                                .taskSets(Arrays.asList(taskSetA.getLogicalId(), "TaskSetB"))
                                .trafficRouting(CfnTrafficRouting.builder()
                                        .prodTrafficRoute(CfnTrafficRoute.builder()
                                                .type(CfnListener.CFN_RESOURCE_TYPE_NAME)
                                                .logicalId(prodListener.getLogicalId())
                                                .build())
                                        .testTrafficRoute(CfnTrafficRoute.builder()
                                                .type(CfnListener.CFN_RESOURCE_TYPE_NAME)
                                                .logicalId(testListener.getLogicalId())
                                                .build())
                                        .targetGroups(Stream.of(blueTG, greenTG).map(CfnElement::getLogicalId).collect(Collectors.toList()))
                                        .build())
                                .build())
                        .build()))
                .build();
    }

    private IFunction createLambdaLifecycleHookFunction(String handler) {
        IFunction function = Function.Builder.create(this, "LambdaLifecycleHookFunction")
                .functionName(prefixName("LifecycleHook"))
                .code(Code.fromAsset("./revisions/lifecyclehooks"))
                .handler(handler)
                .runtime(Runtime.PYTHON_3_9)
                .timeout(Duration.seconds(60))
                .build();
        function.getRole().addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AWSCodeDeployFullAccess"));
        return function;
    }

    private CfnTargetGroup createCfnTargetGroup(final String name, final int port, final IVpc vpc) {
         return CfnTargetGroup.Builder.create(this, name)
                .name(prefixName(name))
                .healthCheckIntervalSeconds(5)
                .healthCheckTimeoutSeconds(2)
                .healthyThresholdCount(2)
                .matcher(CfnTargetGroup.MatcherProperty.builder()
                        .httpCode("200")
                        .build())
                .port(port)
                .protocol(Protocol.HTTP.name())
                .targetType(TargetType.IP.name().toLowerCase())
                .vpcId(vpc.getVpcId())
                .build();
    }

    private CfnListener createCfnListener(final String name, final CfnLoadBalancer lb, final int port, CfnTargetGroup tg) {
        return CfnListener.Builder.create(this, name)
                .defaultActions(Arrays.asList(CfnListener.ActionProperty.builder()
                        .type("forward")
                        .forwardConfig(CfnListener.ForwardConfigProperty.builder()
                                .targetGroups(Arrays.asList(CfnListener.TargetGroupTupleProperty.builder()
                                        .targetGroupArn(tg.getAttrTargetGroupArn())
                                        .weight(1)
                                        .build()))
                                .build())
                        .build()))
                .loadBalancerArn(lb.getRef())
                .port(port)
                .protocol(Protocol.HTTP.name())
                .build();
    }

    private String prefixName(final String name) {
        return "Cdk" + this.getClass().getSimpleName() + name;
    }
}
