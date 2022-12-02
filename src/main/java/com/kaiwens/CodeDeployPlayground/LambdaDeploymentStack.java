package com.kaiwens.CodeDeployPlayground;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CfnOutputProps;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codedeploy.ILambdaApplication;
import software.amazon.awscdk.services.codedeploy.ILambdaDeploymentConfig;
import software.amazon.awscdk.services.codedeploy.LambdaApplication;
import software.amazon.awscdk.services.codedeploy.LambdaDeploymentConfig;
import software.amazon.awscdk.services.codedeploy.LambdaDeploymentGroup;
import software.amazon.awscdk.services.codedeploy.TrafficRouting;
import software.amazon.awscdk.services.lambda.Alias;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.constructs.Construct;

import java.util.Collections;


public class LambdaDeploymentStack extends Stack {
    public LambdaDeploymentStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public LambdaDeploymentStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        final String bucketName = String.format("codedeploy-playground.lambda.%s.%s", getAccount(), getRegion());
        Bucket s3Bucket = Bucket.Builder.create(this, "LambdaCodeBucket")
                .bucketName(bucketName)
                .build();
        BucketDeployment bucketDeployment = BucketDeployment.Builder.create(this, "CodeA")
                .sources(Collections.singletonList(
                        Source.asset("revisions/lambda_app")
                ))
                .destinationBucket(s3Bucket)
                .extract(false)
                .build();

        final String handler = "lambda-A.lambda_handler";
        Function function = Function.Builder.create(this, "TargetLambdaFunction-" + handler)
                .functionName("CDKManagedLambdaDeploymentTargetFunction")
                .code(Code.fromBucket(bucketDeployment.getDeployedBucket(), Fn.select(0, bucketDeployment.getObjectKeys())))
                .handler(handler)
                .runtime(Runtime.PYTHON_3_9)
                .timeout(Duration.seconds(60))
                .description(handler.replace(".lambda_handler", "").replace("lambda-", ""))
                .build();
        function.getNode().addDependency(bucketDeployment.getDeployedBucket());

        Alias alias = Alias.Builder.create(this, "Alias")
                .aliasName("live")
                .version(function.getCurrentVersion())
                .build();
        ILambdaDeploymentConfig deploymentConfig = LambdaDeploymentConfig.Builder.create(this, "DeploymentConfig")
                .deploymentConfigName("CdkManagedLambdaDeploymentConfig")
                .trafficRouting(TrafficRouting.allAtOnce())
                .build();

        ILambdaApplication application = LambdaApplication.Builder.create(this, "Application")
                .applicationName("CdkManagedLambdaApplication")
                .build();

        LambdaDeploymentGroup deploymentGroup = LambdaDeploymentGroup.Builder.create(this, "DeploymentGroup")
                .application(application)
                .deploymentGroupName("CdkManagedLambdaDeploymentGroup")
                .alias(alias)
                .deploymentConfig(deploymentConfig)
                .build();
        new CfnOutput(this, "CurrentVersion", CfnOutputProps.builder().value(function.getCurrentVersion().getVersion()).build());
        new CfnOutput(this, "FunctionAlias", CfnOutputProps.builder().value(alias.getAliasName()).build());
        new CfnOutput(this, "FunctionName", CfnOutputProps.builder().value(function.getFunctionName()).build());
        new CfnOutput(this, "ApplicationName", CfnOutputProps.builder().value(application.getApplicationName()).build());
        new CfnOutput(this, "DeploymentGroupName", CfnOutputProps.builder().value(deploymentGroup.getDeploymentGroupName()).build());
        new CfnOutput(this, "S3Bucket", CfnOutputProps.builder().value(bucketDeployment.getDeployedBucket().getBucketName()).build());
        new CfnOutput(this, "S3Key", CfnOutputProps.builder().value(Fn.select(0, bucketDeployment.getObjectKeys())).build());
    }
}
