package com.kaiwens.CodeDeployPlayground;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.codebuild.BuildEnvironmentVariable;
import software.amazon.awscdk.services.codebuild.BuildSpec;
import software.amazon.awscdk.services.codebuild.PipelineProject;
import software.amazon.awscdk.services.codecommit.Code;
import software.amazon.awscdk.services.codecommit.Repository;
import software.amazon.awscdk.services.codedeploy.ILambdaApplication;
import software.amazon.awscdk.services.codedeploy.LambdaApplication;
import software.amazon.awscdk.services.codepipeline.Action;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.StageOptions;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildAction;
import software.amazon.awscdk.services.codepipeline.actions.CodeCommitSourceAction;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Alias;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.IFunction;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.Bucket;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CodePipelineLambdaStack extends Stack {

    private static final String LAMBDA_FUNCTION_NAME = "CDK-managed-codebuild-function-3";
    private static final String LAMBDA_FUNCTION_ALIAS = "live";
    private static final String LAMBDA_HANDLER_1 = "lambda-A.lambda_handler";
    private static final String LAMBDA_HANDLER_2 = "lambda-B.lambda_handler";
    private static final String LAMBDA_FUNCTION_DESCRIPTION = "This function is crated by CDK-managed CodeBuild. If deleted, CodeBuild will recreate it in the next run.";
    private static final String CODEDEPLOY_APPLICATION_NAME = "CdkManagedCodePipelineLambdaApplication";
    private static final String CODEDEPLOY_DEPLOYMENTGROUP_NAME = "CdkManagedCodePipelineLambdaDeploymentGroup";
    private final String className;

    public CodePipelineLambdaStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        className = this.getClass().getSimpleName();

        Role lambdaExecutionRole = Role.Builder.create(this, "LambdaExecutionRole")
                .inlinePolicies(Collections.singletonMap("lambda",
                        PolicyDocument.Builder.create().statements(
                                Arrays.asList(
                                        PolicyStatement.Builder.create()
                                                .actions(Arrays.asList("*"))
                                                .effect(Effect.DENY)
                                                .resources(Arrays.asList("*"))
                                                .build()
                                )).build()
                ))
                .assumedBy(ServicePrincipal.Builder.create("lambda").build())
                .build();

        IFunction function = createLambdaFunction(LAMBDA_FUNCTION_NAME, "lambda-A.lambda_handler", lambdaExecutionRole);
        Alias alias = Alias.Builder.create(this, "Alias")
                .aliasName(LAMBDA_FUNCTION_ALIAS)
                .version(function.getLatestVersion())
                .build();

        Bucket artifactBucket = Bucket.Builder.create(this, "CdkManagedArtifactBucket")
                .bucketName(String.format("CdkManaged-%s-%s-%s", className, getAccount(), getRegion()).toLowerCase())
                .build();

        Role codedeployServiceRole = createCodeDeployResources(artifactBucket);
        Role buildProjectRole = Role.Builder.create(this, "BuildActionRole")
                .roleName(String.format("CdkManagedBuildActionRole-%s-%s", className, getRegion()))
                .assumedBy(ServicePrincipal.Builder.create("codebuild").build())
                .managedPolicies(Arrays.asList(ManagedPolicy.fromAwsManagedPolicyName("AWSCodeDeployFullAccess")))
                .inlinePolicies(
                        Collections.singletonMap("lambda",
                                PolicyDocument.Builder.create().statements(
                                        Arrays.asList(
                                                PolicyStatement.Builder.create()
                                                        .actions(Arrays.asList(
                                                                "lambda:CreateFunction",
                                                                "lambda:GetFunction",
                                                                "lambda:PublishVersion",
                                                                "lambda:GetAlias",
                                                                "lambda:UpdateAlias",
                                                                "lambda:UpdateFunctionConfiguration",
                                                                "lambda:CreateAlias"
                                                        ))
                                                        .effect(Effect.ALLOW)
                                                        .resources(Arrays.asList(
                                                                String.format("arn:aws:lambda:%s:%s:function:%s", getRegion(), getAccount(), LAMBDA_FUNCTION_NAME),
                                                                String.format("arn:aws:lambda:%s:%s:function:%s:*", getRegion(), getAccount(), LAMBDA_FUNCTION_NAME)
                                                        ))
                                                        .build(),
                                                PolicyStatement.Builder.create()
                                                        .actions(Arrays.asList("iam:PassRole"))
                                                        .effect(Effect.ALLOW)
                                                        .resources(Arrays.asList(
                                                                lambdaExecutionRole.getRoleArn(),
                                                                codedeployServiceRole.getRoleArn()
                                                        ))
                                                        .build(),
                                                PolicyStatement.Builder.create()
                                                        .actions(Arrays.asList("sts:GetCallerIdentity"))
                                                        .effect(Effect.ALLOW)
                                                        .resources(Arrays.asList("*"))
                                                        .build()
                                        )).build()
                        )
                )
                .build();

        Role deployProjectRole = Role.Builder.create(this, "DeployActionRole")
                .roleName("CdkManagedDeployActionRole-" + className)
                .assumedBy(ServicePrincipal.Builder.create("codebuild").build())
                .managedPolicies(Arrays.asList(ManagedPolicy.fromAwsManagedPolicyName("AWSCodeDeployDeployerAccess")))
                .build();

        PipelineProject buildProject = PipelineProject.Builder.create(this, "BuildProject")
                .projectName("BuildProject")
                .role(buildProjectRole)
                .buildSpec(BuildSpec.fromSourceFilename("build_action_input/buildspec.yml"))
                .build();

        Repository repository = Repository.Builder.create(this, "Repository")
                .repositoryName("CdkManagedRepository")
                .description(String.format("Managed by the %s", this.getClass().getName()))
                .code(Code.fromDirectory("./revisions/codesuite"))
                .build();

        Artifact sourceOutput = new Artifact("sourceOutput");
        Artifact buildOutputForDeployAction = new Artifact("BO_DeployAction");
        Artifact buildOutputForCdedeployAppSpec = new Artifact("BO_CDAppSpec");

        Action sourceAction = CodeCommitSourceAction.Builder.create()
                .actionName("CodeCommitSourceAction")
                .repository(repository)
                .branch("main")
                .output(sourceOutput)
                .build();

        Map<String, BuildEnvironmentVariable> buildEnvs = new HashMap<>();
        buildEnvs.put("LAMBDA_FUNCTION_NAME",
                BuildEnvironmentVariable.builder().value(LAMBDA_FUNCTION_NAME)
                .build());
        buildEnvs.put("LAMBDA_FUNCTION_ALIAS",
                BuildEnvironmentVariable.builder().value(LAMBDA_FUNCTION_ALIAS)
                        .build());
        buildEnvs.put("LAMBDA_FUNCTION_DESCRIPTION",
                BuildEnvironmentVariable.builder().value(LAMBDA_FUNCTION_DESCRIPTION)
                        .build());
        buildEnvs.put("LAMBDA_HANDLER_1",
                BuildEnvironmentVariable.builder().value(LAMBDA_HANDLER_1)
                        .build());
        buildEnvs.put("LAMBDA_HANDLER_2",
                BuildEnvironmentVariable.builder().value(LAMBDA_HANDLER_2)
                        .build());
        buildEnvs.put("CODEDEPLOY_APPLICATION_NAME",
                BuildEnvironmentVariable.builder().value(CODEDEPLOY_APPLICATION_NAME)
                        .build());
        buildEnvs.put("CODEDEPLOY_DEPLOYMENTGROUP_NAME",
                BuildEnvironmentVariable.builder().value(CODEDEPLOY_DEPLOYMENTGROUP_NAME)
                        .build());
        buildEnvs.put("CODEDEPLOY_SERVICE_ROLE_ARN",
                BuildEnvironmentVariable.builder().value(codedeployServiceRole.getRoleArn())
                        .build());
//        buildEnvs.put("SECONDARY_ARTIFACT_BUCKET_NAME",
//                BuildEnvironmentVariable.builder().value(buildOutputForCdedeployAppSpec.getBucketName())
//                        .build());
        buildEnvs.put("SECONDARY_ARTIFACT_NAME",
                BuildEnvironmentVariable.builder().value(buildOutputForCdedeployAppSpec.getArtifactName())
                        .build());
//        buildEnvs.put("SECONDARY_ARTIFACT_OBJECT_KEY",
//                BuildEnvironmentVariable.builder().value(buildOutputForCdedeployAppSpec.getObjectKey())
//                        .build());
//        buildEnvs.put("SECONDARY_ARTIFACT_S3_LOCATION",
//                BuildEnvironmentVariable.builder().value(buildOutputForCdedeployAppSpec.getS3Location())
//                        .build());




        Action buildAction = CodeBuildAction.Builder.create()
                .actionName("CodeBuild")
                .environmentVariables(buildEnvs)
                .project(buildProject)
                .input(sourceOutput)
                .outputs(Arrays.asList(buildOutputForDeployAction, buildOutputForCdedeployAppSpec))
                .build();

        PipelineProject deployProject = PipelineProject.Builder.create(this, "DeployProject")
                .projectName("DeploymentProject")
                .role(deployProjectRole)
                .buildSpec(BuildSpec.fromSourceFilename("deploy_action_input/buildspec.yml"))
                .build();

        Action deployAction = CodeBuildAction.Builder.create()
                .actionName("DeployByBuild")
                .environmentVariables(buildEnvs)
                .project(deployProject)
                .input(buildOutputForDeployAction)
                .extraInputs(Arrays.asList(buildOutputForCdedeployAppSpec))
                .build();

        Pipeline pipeline = Pipeline.Builder.create(this, "codepipeline")
                .pipelineName("CDKManagedCodePipelineForLambda")
                .crossAccountKeys(false)
                .artifactBucket(artifactBucket)
                .build();

//        buildProject.addSecondaryArtifact(Artifacts.s3(S3ArtifactsProps.builder()
//                .bucket(artifactBucket)
//                .identifier("codedeploy")
//                .name("bundle.zip")
//                .path("CodeDeployAppSpec")
//                .build()));

        pipeline.addStage(StageOptions.builder()
                .stageName("Source")
                .actions(Collections.singletonList(sourceAction))
                .build());

        pipeline.addStage(StageOptions.builder()
                .stageName("Build")
                .actions(Collections.singletonList(buildAction))
                .build());

        pipeline.addStage(StageOptions.builder()
                .stageName("Deploy")
                .actions(Collections.singletonList(deployAction))
                .build());
    }

    private IFunction createLambdaFunction(String functionName, String handler, IRole role) {
        IFunction function = Function.Builder.create(this, "LambdaTargetFunction")
                .functionName(functionName)
                .code(software.amazon.awscdk.services.lambda.Code.fromAsset("./revisions/lambda_app"))
                .handler(handler)
                .runtime(Runtime.PYTHON_3_9)
                .role(role)
                .timeout(Duration.seconds(60))
                .build();
        return function;
    }

    private Role createCodeDeployResources(Bucket artifactBucket) {
        ILambdaApplication application = LambdaApplication.Builder.create(this, "Application")
                .applicationName(CODEDEPLOY_APPLICATION_NAME)
                .build();

        Role serviceRole = Role.Builder.create(this, "CodeDeployServiceRole")
                .roleName(String.format("CdkManagedCDServiceRole-%s-%s", className, getRegion()))
                .managedPolicies(Arrays.asList(ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSCodeDeployRoleForLambda")))
                .inlinePolicies(Collections.singletonMap("s3",
                        PolicyDocument.Builder.create().statements(
                                Arrays.asList(
                                        PolicyStatement.Builder.create()
                                                .actions(Arrays.asList("s3:GetObject", "s3:GetObjectVersion"))
                                                .effect(Effect.ALLOW)
                                                .resources(Arrays.asList(artifactBucket.getBucketArn() + "/*"))
                                                .build()
                                )).build()
                ))
                .assumedBy(ServicePrincipal.Builder.create("codedeploy").build())
                .build();
        return serviceRole;
    }
}
