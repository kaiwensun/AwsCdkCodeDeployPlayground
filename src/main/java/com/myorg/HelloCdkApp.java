package com.myorg;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;


public class HelloCdkApp {

    public static void main(final String[] args) {
        App app = new App();

        StackProps.Builder stackPropsBuilder = StackProps.builder()
                .env(Environment.builder()
                        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                        .region(System.getenv("CDK_DEFAULT_REGION"))
                        .build());

//        new HelloCdkStack(app, "HelloCdkStack", stackPropsBuilder.stackName("HelloCdkStack").build());

        new EcsServiceStack(app, "CdkEcsServiceStack", stackPropsBuilder.stackName("CdkEcsServiceStack").build());

        app.synth();
    }
}
