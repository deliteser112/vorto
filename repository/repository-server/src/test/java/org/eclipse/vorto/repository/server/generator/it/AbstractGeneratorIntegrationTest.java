/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.vorto.repository.server.generator.it;

import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;
import org.eclipse.vorto.generators.runner.GeneratorRunner;
import org.eclipse.vorto.repository.server.it.AbstractIntegrationTest;
import org.eclipse.vorto.repository.web.core.dto.ModelContent;
import org.junit.runner.RunWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.util.HashMap;
import java.util.Random;

import static org.junit.Assert.fail;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@RunWith(SpringJUnit4ClassRunner.class)
public abstract class AbstractGeneratorIntegrationTest extends AbstractIntegrationTest {

    protected MockMvc vortoMockMvc;

    protected MockMvc generatorMockMvc;

    protected int generator_port;

    protected Gson gson = new Gson();

    @Override protected void setUpTest() throws Exception {
        vortoMockMvc = mockMvc;
        Random ran = new Random();
        generator_port = ran.nextInt(65535);
        while((generator_port == port || generator_port <= 10000)){
            generator_port = ran.nextInt(65535); // Max number of ports
        }

        HashMap<String, Object> generatorProps = new HashMap<>();
        generatorProps.put("vorto.serverUrl",
            "http://localhost:" + Integer.toString(port) + "/infomodelrepository");
        generatorProps.put("vorto.tenantId", "default");
        generatorProps.put("server.serviceUrl",
            "http://localhost:" + Integer.toString(generator_port) + "/generatorgateway");

        ConfigurableApplicationContext generatorContext =
            new SpringApplicationBuilder(GeneratorRunner.class).properties(generatorProps).run(
                new String[] {"--server.port=" + Integer.toString(generator_port),
                    "--spring.jmx.enabled=false", "--spring.security.enabled=false",
                    "--management.security.enabled=false", "--security.basic.enabled=false",
                    "--server.contextPath=/generatorgateway"});

        generatorMockMvc =
            MockMvcBuilders.webAppContextSetup((WebApplicationContext) generatorContext)
                .apply(springSecurity()).build();
    }

    protected String loadModel(String type, String filename) {
        ModelContent modelContent = new ModelContent();
        modelContent.setType(type);
        try {
            modelContent.setContentDsl(
                IOUtils.toString(new ClassPathResource(filename).getInputStream(), "utf-8"));
        } catch (IOException e) {
            fail("Cannot load test model");
        }
        return gson.toJson(modelContent);
    }

    protected byte[] loadResource(String filename) throws IOException {
        return IOUtils.toByteArray(new ClassPathResource(filename).getInputStream());
    }
}
