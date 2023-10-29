package org.traccar.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.traccar.ProtocolTest;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

public class CarStatsViewerDecoderTest extends ProtocolTest {

	private static final String TEST_URI = "/csv/xxxxxxx";

	// LAT/LON values are changed from original data just for testing in the files
	
	// A big upload
	private static final String CAR_STATS_VIEWER_SAMPLE_UPLOAD_JSON = "car_stats_viewer_sample_upload.json";
	// single drive point to be merged
	private static final String CAR_STATS_VIEWER_SAMPLE_SINGLE_DRIVING_POINT_1_JSON = "car_stats_viewer_sample_single_driving_point_1.json";
	// single drive point with more than 1 second difference to not be merged
	private static final String CAR_STATS_VIEWER_SAMPLE_SINGLE_DRIVING_POINT_2_JSON = "car_stats_viewer_sample_single_driving_point_2.json";

	private static final String DRIVING_POINTS =
			"\"drivingPoints\":[{\"alt\":863.0201,\"distance_delta\":92.17616,\"driving_point_epoch_time\":1697383579222,\"energy_delta\":66.39321,\"lat\":-23.0,\"lon\":-46.0,\"point_marker_type\":2,\"state_of_charge\":0.7}]";
	
	private static final String REQ_START = 
		"{\"abrpPackage\":\"\",\"alt\":868.3861,\"ambientTemperature\":16.0,\"apiVersion\":\"2.1\",\"appVersion\":\"0.25.2.0019\",\"batteryLevel\":45560.0,\"chargePortConnected\":true,\"ignitionState\":\"Accessory\",\"lat\":-23.0,\"lon\":-46.0,\"power\":-2663500.0,\"selectedGear\":\"P\",\"speed\":0.0,\"stateOfCharge\":0.67,\"timestamp\":1697311782394";

	private static final String REQ_1 = 
			REQ_START + "}";
			
	private static final String REQ_2 = 
			REQ_START + "," + DRIVING_POINTS + "}";
	
	
	private DefaultFullHttpRequest createRequest(final String uri, final String data) {
		final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, 
				HttpMethod.POST, uri, 
				Unpooled.copiedBuffer(data, StandardCharsets.UTF_8));
		return request;
	}
	
	public void testRequestPosition(final String data) throws Exception {
		var decoder = inject(new CarStatsViewerDecoder(null));
		verifyPosition(decoder, createRequest(TEST_URI, data));
	}

	public void testRequestPositions(final String data) throws Exception {
		var decoder = inject(new CarStatsViewerDecoder(null));
		verifyPositions(decoder, createRequest(TEST_URI, data));
	}

	public void testRequestNull(final String uri, final String data) throws Exception {
		var decoder = inject(new CarStatsViewerDecoder(null));
		verifyNull(decoder, createRequest(uri, data));
	}


	@Test
	public void testEmpty() throws Exception {
		testRequestNull(TEST_URI, "");
	}

	@Test
	public void testWrongURI() throws Exception {
		testRequestNull("/any", REQ_1);
	}

	@Test
	public void test1() throws Exception {
		testRequestPosition(REQ_1);
	}

	@Test
	public void test2() throws Exception {
		testRequestPositions(REQ_2);
	}

	@Test
	public void testBigRequest() throws Exception {
		final InputStream is = getClass().getClassLoader().getResourceAsStream(CAR_STATS_VIEWER_SAMPLE_UPLOAD_JSON);
		assertNotNull(is, "sample data not found");
		final String data = new String(is.readAllBytes());
		testRequestPositions(data);
	}

	@Test
	public void testSingleDrivingPoint1() throws Exception {
		final InputStream is = getClass().getClassLoader().getResourceAsStream(CAR_STATS_VIEWER_SAMPLE_SINGLE_DRIVING_POINT_1_JSON);
		assertNotNull(is, "sample data not found");
		final String data = new String(is.readAllBytes());
		testRequestPosition(data);
	}

	@Test
	public void testSingleDrivingPoint2() throws Exception {
		final InputStream is = getClass().getClassLoader().getResourceAsStream(CAR_STATS_VIEWER_SAMPLE_SINGLE_DRIVING_POINT_2_JSON);
		assertNotNull(is, "sample data not found");
		final String data = new String(is.readAllBytes());
		testRequestPositions(data);
	}

}
