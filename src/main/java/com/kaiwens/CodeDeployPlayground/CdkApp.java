package com.kaiwens.CodeDeployPlayground;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.sts.StsClient;

import java.util.Arrays;


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


       new ServerDeploymentStack(app, "ServerDeploymentStack", stackPropsBuilder.stackName("ServerDeploymentStack").build());

//
//        // new EcsServiceStack(app, "CdkEcsServiceStack", stackPropsBuilder.stackName("CdkEcsServiceStack").build());
//
        app.synth();
    }
}
