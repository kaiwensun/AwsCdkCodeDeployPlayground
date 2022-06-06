package com.kaiwens.CodeDeployPlayground;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import static org.assertj.core.api.Assertions.assertThat;

public class HelloCdkTest {
    private final static ObjectMapper JSON =
        new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

//    @Test
//    public void testStack() throws IOException {
//        App app = new App();
//        ServerDeploymentStack stack = new ServerDeploymentStack(app, "test");
//
//        // synthesize the stack to a CloudFormation template
//        JsonNode actual = JSON.valueToTree(app.synth().getStackArtifact(stack.getArtifactId()).getTemplate());
//
//        // Update once resources have been added to the stack
//        assertThat(actual.get("Resources")).isNull();
//    }
}
