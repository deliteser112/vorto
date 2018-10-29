package org.eclipse.vorto.mapping.engine.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.eclipse.vorto.mapping.engine.IDataMapper;
import org.eclipse.vorto.mapping.engine.MappingException;
import org.eclipse.vorto.model.runtime.FunctionblockValue;
import org.eclipse.vorto.model.runtime.InfomodelValue;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JsonMappingTest {
	
	private static Gson gson = new GsonBuilder().create();
	
	@Test
	public void testConfigMapping() throws Exception {

		IDataMapper mapper = IDataMapper.newBuilder().withSpecification(new SpecWithConfigMapping())
				.registerScriptEvalProvider(new JavascriptEvalProvider())
				.build();

		String json = "{\"clickType\" : \"DOUBLE\", \"batteryVoltage\": \"2322mV\"}";

		InfomodelValue mappedOutput = mapper.mapSource(gson.fromJson(json, Object.class));

		System.out.println(mappedOutput);

	}

	@Test
	public void testMapping() throws Exception {

		IDataMapper mapper = IDataMapper.newBuilder().withSpecification(new SpecWithCustomFunction())
				.registerScriptEvalProvider(new JavascriptEvalProvider())
				.build();

		String json = "{\"clickType\" : \"DOUBLE\", \"batteryVoltage\": \"2322mV\"}";

		InfomodelValue mappedOutput = mapper.mapSource(gson.fromJson(json, Object.class));

		FunctionblockValue buttonFunctionblockData = mappedOutput.get("button");

		assertEquals(true, (Boolean) buttonFunctionblockData.getStatusProperty("digital_input_state").get().getValue());
		assertEquals(2, buttonFunctionblockData.getStatusProperty("digital_input_count").get().getValue());

		FunctionblockValue voltageFunctionblockData = mappedOutput.get("voltage");

		assertEquals(2322f, voltageFunctionblockData.getStatusProperty("sensor_value").get().getValue());
		assertEquals("mV", voltageFunctionblockData.getStatusProperty("sensor_units").get().getValue());

		System.out.println(gson.toJson(mappedOutput.getProperties()));

	}
	
	@Test(expected = MappingException.class)
	public void testMappingWithMalicousScript() throws Exception {

		IDataMapper mapper = IDataMapper.newBuilder().withSpecification(new SpecWithMaliciousFunction() {

			@Override
			protected String getMaliciousFunctionBody() {
				return "return quit();";
			}
			
		})
				.registerScriptEvalProvider(new JavascriptEvalProvider())
				.build();

		String json = "{\"clickType\" : \"DOUBLE\", \"batteryVoltage\": \"2322mV\"}";

		mapper.mapSource(gson.fromJson(json, Object.class));
	}
	
	@Test(expected = MappingException.class)
	public void testMappingWithMalicousScript2() throws Exception {

		IDataMapper mapper = IDataMapper.newBuilder().withSpecification(new SpecWithMaliciousFunction() {

			@Override
			protected String getMaliciousFunctionBody() {
				return "return exit();";
			}
			
		})
				.registerScriptEvalProvider(new JavascriptEvalProvider())
				.build();

		String json = "{\"clickType\" : \"DOUBLE\", \"batteryVoltage\": \"2322mV\"}";

		mapper.mapSource(gson.fromJson(json, Object.class));
	}
	
	@Test(expected = MappingException.class)
	public void testMappingWithMalicousScript3() throws Exception {

		IDataMapper mapper = IDataMapper.newBuilder().withSpecification(new SpecWithMaliciousFunction() {

			@Override
			protected String getMaliciousFunctionBody() {
				return "while (true) { }";
			}
			
		})
				.registerScriptEvalProvider(new JavascriptEvalProvider())
				.build();

		String json = "{\"clickType\" : \"DOUBLE\", \"batteryVoltage\": \"2322mV\"}";

		mapper.mapSource(gson.fromJson(json, Object.class));
	}
	
	@Test(expected = MappingException.class)
	public void testMappingWithMalicousScript4() throws Exception {

		IDataMapper mapper = IDataMapper.newBuilder().withSpecification(new SpecWithMaliciousFunction() {

			@Override
			protected String getMaliciousFunctionBody() {
				return "for (;;) { }";
			}
			
		})
				.registerScriptEvalProvider(new JavascriptEvalProvider())
				.build();

		String json = "{\"clickType\" : \"DOUBLE\", \"batteryVoltage\": \"2322mV\"}";

		mapper.mapSource(gson.fromJson(json, Object.class));
	}
	
	@Test(expected = MappingException.class)
	public void testMappingWithMalicousScriptUsingJavaImports() throws Exception {

		IDataMapper mapper = IDataMapper.newBuilder().withSpecification(new SpecWithMaliciousFunction() {

			@Override
			protected String getMaliciousFunctionBody() {
				return "load('https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.8.0/highlight.min.js')";
			}
		})
				.registerScriptEvalProvider(new JavascriptEvalProvider())
				.build();

		String json = "{\"clickType\" : \"DOUBLE\", \"batteryVoltage\": \"2322mV\"}";

		mapper.mapSource(gson.fromJson(json, Object.class));
	}

	@Test
	public void testMapDevicePayloadWithInitialValue() {
		IDataMapper mapper = IDataMapper.newBuilder().withSpecification(new SpecWithCustomFunction())
				.registerScriptEvalProvider(new JavascriptEvalProvider())
				.build();

		String json = "{\"clickType\" : \"DOUBLE\", \"batteryVoltage\": \"0mV\"}";

		InfomodelValue mappedOutput = mapper.mapSource(gson.fromJson(json, Object.class));

		FunctionblockValue buttonFunctionblockData = mappedOutput.get("button");

		assertEquals(true, (Boolean) buttonFunctionblockData.getStatusProperty("digital_input_state").get().getValue());
		assertEquals(2, buttonFunctionblockData.getStatusProperty("digital_input_count").get().getValue());

		FunctionblockValue voltageFunctionblockData = mappedOutput.get("voltage");

		assertEquals(0f, voltageFunctionblockData.getStatusProperty("sensor_value").get().getValue());
		assertEquals("mV", voltageFunctionblockData.getStatusProperty("sensor_units").get().getValue());

		System.out.println(mappedOutput);
	}

	@Test
	public void testMapSingleFunctionblockOfInfomodel() {
		IDataMapper mapper = IDataMapper.newBuilder().withSpecification(new SpecWithCustomFunction())
				.registerScriptEvalProvider(new JavascriptEvalProvider())
				.build();

		String json = "{\"clickType\" : \"DOUBLE\"}";

		InfomodelValue mappedOutput = mapper.mapSource(gson.fromJson(json, Object.class));

		FunctionblockValue buttonFunctionblockData = mappedOutput.get("button");

		assertEquals(true, (Boolean) buttonFunctionblockData.getStatusProperty("digital_input_state").get().getValue());
		assertEquals(2, buttonFunctionblockData.getStatusProperty("digital_input_count").get().getValue());

		FunctionblockValue voltageFunctionblockData = mappedOutput.get("voltage");

		assertNull(voltageFunctionblockData);

		System.out.println(mappedOutput);
	}
}
