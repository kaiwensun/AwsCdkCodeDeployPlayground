package com.kaiwens.CodeDeployPlayground;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.sts.StsClient;


public class CdkApp {

    public static void main(final String[] args) {
        App app = new App();
        Region region = DefaultAwsRegionProviderChain.builder().build().getRegion();
        String accountId;
        try (StsClient sts = StsClient.create()) {
            accountId = sts.getCallerIdentity().account();
        }

        StackProps.Builder stackPropsBuilder = StackProps.builder()
                .env(Environment.builder()
                        .account(accountId)
                        .region(region.toString())
                        .build());


        new ServerDeploymentStack(app,
                "ServerDeploymentStack",
                stackPropsBuilder
                        .stackName("ServerDeploymentStack")
                        .description("Managed by CDK package KaiwensCodeDeployPlayground")
                        .build(),
                ServerDeploymentStack.LBType.CLB);


         new EcsFargateDeploymentStack(app,
                 "EcsDeploymentStack",
                 stackPropsBuilder
                         .stackName("EcsFargateDeploymentStack")
                         .description("Managed by CDK package KaiwensCodeDeployPlayground")
                         .build());

        new LambdaDeploymentStack(app,
                "LambdaDeploymentStack",
                stackPropsBuilder
                        .stackName("LambdaDeploymentStack")
                        .description("Managed by CDK package KaiwensCodeDeployPlayground")
                        .build());

        new EcsFargateBGHookStack(app,
                "EcsFargateBGHookStack",
                stackPropsBuilder
                        .stackName("EcsFargateBGHookStack")
                        .description("Managed by CDK package KaiwensCodeDeployPlayground")
                        .build());

        new CodePipelineLambdaStack(app,
                "CodePipelineLambdaStack",
                stackPropsBuilder
                        .stackName("CodePipelineLambdaStack")
                        .description("Managed by CDK package KaiwensCodeDeployPlayground")
                        .build());

        app.synth();
    }
}
