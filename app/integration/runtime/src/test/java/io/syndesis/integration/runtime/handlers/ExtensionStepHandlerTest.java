/*
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.integration.runtime.handlers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import io.syndesis.common.model.action.ConnectorAction;
import io.syndesis.common.model.action.ConnectorDescriptor;
import io.syndesis.common.model.action.StepAction;
import io.syndesis.common.model.action.StepDescriptor;
import io.syndesis.common.model.integration.StepKind;
import io.syndesis.extension.api.Step;
import io.syndesis.integration.runtime.IntegrationTestSupport;
import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Handler;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.DefaultPropertiesParser;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.properties.PropertiesParser;
import org.apache.camel.model.PipelineDefinition;
import org.apache.camel.model.ProcessDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SetHeaderDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spring.SpringCamelContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertyResolver;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext
@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        ExtensionStepHandlerTest.TestConfiguration.class
    },
    properties = {
        "spring.main.banner-mode = off",
        "logging.level.io.syndesis.integration.runtime = DEBUG",
        "twitter-timeline-1.accessToken = at",
        "twitter-timeline-1.accessTokenSecret = ats",
        "twitter-timeline-1.consumerKey = ck",
        "twitter-timeline-1.consumerSecret = cs"
    }
)
@TestExecutionListeners(
    listeners = {
        DependencyInjectionTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class
    }
)
@SuppressWarnings({"PMD.ExcessiveImports", "PMD.JUnitTestContainsTooManyAsserts"})
public class ExtensionStepHandlerTest extends IntegrationTestSupport {
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testEndpointExtensionStepHandler() throws Exception {
        final CamelContext context = new SpringCamelContext(applicationContext);

        try {
            final RouteBuilder routeBuilder = newIntegrationRouteBuilder(
                new io.syndesis.common.model.integration.Step.Builder()
                    .id("step-1")
                    .stepKind(StepKind.endpoint)
                    .action(new ConnectorAction.Builder()
                        .descriptor(new ConnectorDescriptor.Builder()
                            .componentScheme("direct")
                            .putConfiguredProperty("name", "start")
                            .build())
                        .build())
                    .build(),
                new io.syndesis.common.model.integration.Step.Builder()
                    .id("step-2")
                    .stepKind(StepKind.extension)
                    .action(new StepAction.Builder()
                        .descriptor(new StepDescriptor.Builder()
                            .kind(StepAction.Kind.ENDPOINT)
                            .entrypoint("log:myLog")
                            .build())
                        .build())
                    .putConfiguredProperty("Property-1", "Val-1")
                    .putConfiguredProperty("Property-2", "Val-2")
                    .build(),
                new io.syndesis.common.model.integration.Step.Builder()
                    .id("step-3")
                    .stepKind(StepKind.endpoint)
                    .action(new ConnectorAction.Builder()
                        .descriptor(new ConnectorDescriptor.Builder()
                            .componentScheme("mock")
                            .putConfiguredProperty("name", "result")
                            .build())
                        .build())
                    .build()
            );

            // Set up the camel context
            context.addRoutes(routeBuilder);
            context.setAutoStartup(false);
            context.start();

            // Dump routes as XML for troubleshooting
            dumpRoutes(context);

            List<RouteDefinition> routes = context.getRouteDefinitions();
            assertThat(routes).hasSize(1);

            RouteDefinition route = context.getRouteDefinitions().get(0);
            assertThat(route).isNotNull();
            assertThat(route.getInputs()).hasSize(1);
            assertThat(route.getInputs().get(0)).hasFieldOrPropertyWithValue("uri", "direct:start");
            assertThat(route.getOutputs()).hasSize(4);
            assertThat(route.getOutputs().get(0)).isInstanceOf(SetHeaderDefinition.class);
            assertThat(route.getOutputs().get(1)).isInstanceOf(ProcessDefinition.class);
            assertThat(route.getOutputs().get(2)).isInstanceOf(PipelineDefinition.class);
            assertThat(route.getOutputs().get(2).getOutputs()).hasSize(5);
            assertThat(route.getOutputs().get(2).getOutputs().get(0)).isInstanceOf(SetHeaderDefinition.class);
            assertThat(route.getOutputs().get(2).getOutputs().get(1)).isInstanceOf(SetHeaderDefinition.class);
            assertThat(route.getOutputs().get(2).getOutputs().get(1)).hasFieldOrPropertyWithValue("headerName", "Property-1");
            assertThat(SetHeaderDefinition.class.cast(route.getOutputs().get(2).getOutputs().get(1)).getExpression()).hasFieldOrPropertyWithValue("expression", "Val-1");
            assertThat(route.getOutputs().get(2).getOutputs().get(2)).isInstanceOf(SetHeaderDefinition.class);
            assertThat(route.getOutputs().get(2).getOutputs().get(2)).hasFieldOrPropertyWithValue("headerName", "Property-2");
            assertThat(SetHeaderDefinition.class.cast(route.getOutputs().get(2).getOutputs().get(2)).getExpression()).hasFieldOrPropertyWithValue("expression", "Val-2");
            assertThat(route.getOutputs().get(2).getOutputs().get(3)).isInstanceOf(ToDefinition.class);
            assertThat(route.getOutputs().get(2).getOutputs().get(3)).hasFieldOrPropertyWithValue(
                "uri",
                "log:myLog"
            );
            assertThat(route.getOutputs().get(2).getOutputs().get(4)).isInstanceOf(ProcessDefinition.class);
            assertThat(route.getOutputs().get(3)).isInstanceOf(PipelineDefinition.class);
        } finally {
            context.stop();
        }
    }

    @Test
    public void testBeanExtensionStepHandler() throws Exception {
        final CamelContext context = new SpringCamelContext(applicationContext);

        try {
            final RouteBuilder routeBuilder = newIntegrationRouteBuilder(
                new io.syndesis.common.model.integration.Step.Builder()
                    .id("step-1")
                    .stepKind(StepKind.endpoint)
                    .action(new ConnectorAction.Builder()
                        .descriptor(new ConnectorDescriptor.Builder()
                            .componentScheme("direct")
                            .putConfiguredProperty("name", "start")
                            .build())
                        .build())
                    .build(),
                new io.syndesis.common.model.integration.Step.Builder()
                    .id("step-2")
                    .stepKind(StepKind.extension)
                    .action(new StepAction.Builder()
                        .descriptor(new StepDescriptor.Builder()
                            .kind(StepAction.Kind.BEAN)
                            .entrypoint("io.syndesis.integration.runtime.handlers.ExtensionStepHandlerTest$MyExtension::action")
                            .build())
                        .build())
                    .putConfiguredProperty("param1", "Val-1")
                    .putConfiguredProperty("param2", "Val-2")
                    .build(),
                new io.syndesis.common.model.integration.Step.Builder()
                    .id("step-3")
                    .stepKind(StepKind.endpoint)
                    .action(new ConnectorAction.Builder()
                        .descriptor(new ConnectorDescriptor.Builder()
                            .componentScheme("mock")
                            .putConfiguredProperty("name", "result")
                            .build())
                        .build())
                    .build()
            );

            // Set up the camel context
            context.addRoutes(routeBuilder);
            context.setAutoStartup(false);
            context.start();

            // Dump routes as XML for troubleshooting
            dumpRoutes(context);

            List<RouteDefinition> routes = context.getRouteDefinitions();
            assertThat(routes).hasSize(1);

            RouteDefinition route = context.getRouteDefinitions().get(0);
            assertThat(route).isNotNull();
            assertThat(route.getInputs()).hasSize(1);
            assertThat(route.getInputs().get(0)).hasFieldOrPropertyWithValue("uri", "direct:start");
            assertThat(route.getOutputs()).hasSize(4);
            assertThat(route.getOutputs().get(0)).isInstanceOf(SetHeaderDefinition.class);
            assertThat(route.getOutputs().get(1)).isInstanceOf(ProcessDefinition.class);
            assertThat(route.getOutputs().get(2)).isInstanceOf(PipelineDefinition.class);
            assertThat(route.getOutputs().get(2).getOutputs()).hasSize(3);
            assertThat(route.getOutputs().get(2).getOutputs().get(0)).isInstanceOf(SetHeaderDefinition.class);
            assertThat(route.getOutputs().get(2).getOutputs().get(1)).isInstanceOf(ToDefinition.class);
            assertThat(route.getOutputs().get(2).getOutputs().get(1)).hasFieldOrPropertyWithValue(
                "uri",
                "class:io.syndesis.integration.runtime.handlers.ExtensionStepHandlerTest$MyExtension?method=action&bean.param1=Val-1&bean.param2=Val-2"
            );
            assertThat(route.getOutputs().get(2).getOutputs().get(2)).isInstanceOf(ProcessDefinition.class);
            assertThat(route.getOutputs().get(3)).isInstanceOf(PipelineDefinition.class);
        } finally {
            context.stop();
        }
    }

    @Test
    public void testStepExtensionStepHandler() throws Exception {
        final CamelContext context = new SpringCamelContext(applicationContext);

        try {
            final RouteBuilder routeBuilder = newIntegrationRouteBuilder(
                new io.syndesis.common.model.integration.Step.Builder()
                    .id("step-1")
                    .stepKind(StepKind.endpoint)
                    .action(new ConnectorAction.Builder()
                        .descriptor(new ConnectorDescriptor.Builder()
                            .componentScheme("direct")
                            .putConfiguredProperty("name", "start")
                            .build())
                        .build())
                    .build(),
                new io.syndesis.common.model.integration.Step.Builder()
                    .id("step-2")
                    .stepKind(StepKind.extension)
                    .action(new StepAction.Builder()
                        .descriptor(new StepDescriptor.Builder()
                            .kind(StepAction.Kind.STEP)
                            .entrypoint("io.syndesis.integration.runtime.handlers.ExtensionStepHandlerTest$MyStepExtension")
                            .build())
                        .build())
                    .putConfiguredProperty("param1", "Val-1")
                    .putConfiguredProperty("param2", "Val-2")
                    .build(),
                new io.syndesis.common.model.integration.Step.Builder()
                    .id("step-3")
                    .stepKind(StepKind.endpoint)
                    .action(new ConnectorAction.Builder()
                        .descriptor(new ConnectorDescriptor.Builder()
                            .componentScheme("mock")
                            .putConfiguredProperty("name", "result")
                            .build())
                        .build())
                    .build()
            );

            // Set up the camel context
            context.addRoutes(routeBuilder);
            context.setAutoStartup(false);
            context.start();

            // Dump routes as XML for troubleshooting
            dumpRoutes(context);

            RouteDefinition route = context.getRouteDefinitions().get(0);
            assertThat(route).isNotNull();
            assertThat(route.getInputs()).hasSize(1);
            assertThat(route.getInputs().get(0)).hasFieldOrPropertyWithValue("uri", "direct:start");
            assertThat(route.getOutputs()).hasSize(4);
            assertThat(route.getOutputs().get(0)).isInstanceOf(SetHeaderDefinition.class);
            assertThat(route.getOutputs().get(1)).isInstanceOf(ProcessDefinition.class);
            assertThat(route.getOutputs().get(2)).isInstanceOf(PipelineDefinition.class);
            assertThat(route.getOutputs().get(2).getOutputs()).hasSize(4);
            assertThat(route.getOutputs().get(2).getOutputs().get(0)).isInstanceOf(SetHeaderDefinition.class);
            assertThat(route.getOutputs().get(2).getOutputs().get(1)).isInstanceOf(SetHeaderDefinition.class);
            assertThat(route.getOutputs().get(2).getOutputs().get(1)).hasFieldOrPropertyWithValue("headerName", "param1");
            assertThat(SetHeaderDefinition.class.cast(route.getOutputs().get(2).getOutputs().get(1)).getExpression()).hasFieldOrPropertyWithValue("expression", "Val-1");
            assertThat(route.getOutputs().get(2).getOutputs().get(2)).isInstanceOf(SetHeaderDefinition.class);
            assertThat(route.getOutputs().get(2).getOutputs().get(2)).hasFieldOrPropertyWithValue("headerName", "param2");
            assertThat(SetHeaderDefinition.class.cast(route.getOutputs().get(2).getOutputs().get(2)).getExpression()).hasFieldOrPropertyWithValue("expression", "Val-2");
            assertThat(route.getOutputs().get(2).getOutputs().get(3)).isInstanceOf(ProcessDefinition.class);
            assertThat(route.getOutputs().get(3)).isInstanceOf(PipelineDefinition.class);
        } finally {
            context.stop();
        }
    }

    // ***************************
    //
    // ***************************

    @Configuration
    public static class TestConfiguration {
        @Bean
        public PropertiesParser propertiesParser(PropertyResolver propertyResolver) {
            return new DefaultPropertiesParser() {
                @Override
                public String parseProperty(String key, String value, Properties properties) {
                    return propertyResolver.getProperty(key);
                }
            };
        }

        public @Bean(destroyMethod = "")
        PropertiesComponent properties(PropertiesParser parser) {
            PropertiesComponent pc = new PropertiesComponent();
            pc.setPropertiesParser(parser);
            return pc;
        }
    }

    public static class MyExtension {
        private String param1;
        private String param2;

        public String getParam1() {
            return param1;
        }

        public void setParam1(String param1) {
            this.param1 = param1;
        }

        public String getParam2() {
            return param2;
        }

        public void setParam2(String param2) {
            this.param2 = param2;
        }

        @Handler
        public void handle(@Body String body) {
            // NO-OP
        }
    }

    public static class MyStepExtension implements Step {
        @Override
        public Optional<ProcessorDefinition> configure(CamelContext context, ProcessorDefinition definition, Map<String, Object> map) {
            map.forEach((k, v) -> definition.setHeader(k).constant(v));

            return Optional.empty();
        }
    }
}
