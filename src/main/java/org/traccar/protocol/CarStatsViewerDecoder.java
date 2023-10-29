package org.traccar.protocol;

import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.BaseHttpProtocolDecoder;
import org.traccar.Protocol;
import org.traccar.model.Command;
import org.traccar.model.Position;
import org.traccar.session.DeviceSession;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Car Stats Viewer Decoder
 * 
 * https://github.com/Ixam97/CarStatsViewer/blob/master/docs/APIDOC.md
 * 
 * ID can be get from 
 *    URI path - /csv/ID
 *    Authorization header - USER:PASSWORD mapped to (ID)
 * 
 */
public class CarStatsViewerDecoder  extends BaseHttpProtocolDecoder {

	private static final String AUTH_PREFIX_BASIC = "Basic ";
	private static final Logger LOGGER = LoggerFactory.getLogger(CarStatsViewerDecoder.class);

	public CarStatsViewerDecoder(Protocol protocol) {
        super(protocol);
    }
	
	/**
	 * Use specific path for requests, this way can be processed by same SSL server as traccar web interface
	 */
	private static final String URI_PREFIX = "/csv/";

	private class CSVProcessor {


		private static final String CSV_DRIVING_POINT_EPOCH_TIME = "driving_point_epoch_time";

		private static final String CSV_DRIVING_POINTS = "drivingPoints";

		private static final String CSV_ABRP_PACKAGE = "abrpPackage";

		private static final String CSV_STATE_OF_CHARGE = "stateOfCharge";
		private static final String CSV_DRIVING_POINT_STATE_OF_CHARGE = "state_of_charge";

		private static final String CSV_IGNITION_STATE = "ignitionState";
		private static final String TRACCAR_IGNITION_STATE = CSV_IGNITION_STATE;

		private static final String CSV_AMBIENT_TEMPERATURE = "ambientTemperature";

		private static final String CSV_SPEED = "speed";

		private static final String CSV_TIMESTAMP = "timestamp";

		private static final String CSV_ALTITUDE = "alt";

		private static final String CSV_LONGITUDE = "lon";

		private static final String CSV_LATITUDE = "lat";

		private static final String CSV_API_VERSION = "apiVersion";
		
		private static final String CSV_APP_VERSION = "appVersion";
		private static final String TRACCAR_CSV_APP_VERSION = CSV_APP_VERSION;

		private static final String CSV_CHARGE_PORT_CONNECTED = "chargePortConnected";
		private static final String TRACCAR_CHARGE_PORT_CONNECTED = CSV_CHARGE_PORT_CONNECTED;

		private static final String CSV_SELECTED_GEAR = "selectedGear";
		private static final String TRACCAR_SELECTED_GEAR = "gear";

		private static final String CSV_BATTERY_LEVEL_WH = "batteryLevel";
		// traccar have "batteryLevel" for percentage, because of this the name must be different
		private static final String TRACCAR_BATTERY_LEVEL_WH = "batteryLevel.wh";
		
		private static final String CSV_POWER = "power";
		private static final String TRACCAR_POWER_M_W = "power.mW";

		private static final String CSV_CHARGING_SESSIONS = "chargingSessions";
		private static final String TRACCAR_CHARGING_SESSIONS = "chargingSessions";

		private final List<Position> positions = new ArrayList<>();
		
		private final DeviceSession deviceSession;

		public CSVProcessor(final DeviceSession deviceSession) {
			this.deviceSession = deviceSession;
		}
		
		public void process(final String contentData) {
	        if (contentData.startsWith("{")) {
	            final JsonObject jsonRoot = Json.createReader(new StringReader(contentData)).readObject();
	            
	            new CSVContextMain().process(jsonRoot);
	        } else {
	        	LOGGER.error("Not JSON data {}", contentData);
	        }

		}

		private abstract class CSVContext {
			// Json is readonly, use this to know properties that was processed
			protected final HashSet<String> used = new HashSet<>();
			protected boolean ignore = false;
			
			protected Position process(final JsonObject jsonRoot) {
	            LOGGER.info("{} Process json {}", getClass().getSimpleName(), jsonRoot);
	            

				final Position position = processLocation(jsonRoot); 

				processExtraData(position, jsonRoot);
				//	            position.set("fullJsonData", jsonRoot.toString());


				// Cria novo objeto somente com valores nao processados
				final JsonObjectBuilder builder = Json.createObjectBuilder();
				jsonRoot.entrySet().forEach(a -> {
				  if (!used.contains(a.getKey())) {
					  builder.add(a.getKey(), a.getValue());
				  }
				});
				
				
				final JsonObject unprocessed = builder.build();
				if (!unprocessed.values().isEmpty()) {
					position.set("unprocessedJsonData", unprocessed.toString());
					LOGGER.info("Remaining in json : {}", unprocessed);
				}
				
				if (position.getFixTime() == null) {
				    position.setTime(new Date());
				}

				if (!ignore) {
					positions.add(position);
				}
				LOGGER.debug("{} {}", getClass().getName(), position.getAttributes());
				return position;
			}
			
			protected void processExtraData(final Position position, final JsonObject jsonRoot) {
				
			}

			protected Position processLocation(final JsonObject jsonRoot) {
				final Position position = new Position(getProtocolName());
		        position.setValid(true);
		        position.setDeviceId(deviceSession.getDeviceId());
				
				Double latitude = null;
				Double longitude = null;

				if (jsonRoot.containsKey(CSV_LATITUDE) && jsonRoot.containsKey(CSV_LONGITUDE)) {
					latitude = getDoubleFromJson(jsonRoot, CSV_LATITUDE);
					longitude = getDoubleFromJson(jsonRoot, CSV_LONGITUDE);
				}
				if (jsonRoot.containsKey(CSV_ALTITUDE)) {
				    position.setAltitude(getDoubleFromJson(jsonRoot, CSV_ALTITUDE));
				}
				
				if (latitude != null && longitude != null) {
				    position.setLatitude(latitude);
				    position.setLongitude(longitude);
				} else {
				    getLastLocation(position, position.getDeviceTime());
				}
				
				if (jsonRoot.containsKey(CSV_STATE_OF_CHARGE)) {
				    position.set(Position.KEY_BATTERY_LEVEL, getDoubleFromJson(jsonRoot, CSV_STATE_OF_CHARGE)*100);
				} else if (jsonRoot.containsKey(CSV_DRIVING_POINT_STATE_OF_CHARGE)) {
				    position.set(Position.KEY_BATTERY_LEVEL, getDoubleFromJson(jsonRoot, CSV_DRIVING_POINT_STATE_OF_CHARGE)*100);
				}

				if (jsonRoot.containsKey(CSV_TIMESTAMP)) {
				    position.setTime(new Date(getLongFromJson(jsonRoot, CSV_TIMESTAMP)));
				} else if (jsonRoot.containsKey(CSV_DRIVING_POINT_EPOCH_TIME)) {
				    position.setTime(new Date(getLongFromJson(jsonRoot, CSV_DRIVING_POINT_EPOCH_TIME)));
				}

				return position;
			}

			private boolean isEmpty(JsonValue value) {
				return ((value instanceof JsonString) && StringUtils.isEmpty(((JsonString)value).getString()))
						|| StringUtils.isEmpty(value.toString());
			}

			// Se for branco indica que utilizou para nao sobrar 
			protected void checkIfNotEmpty(JsonObject jsonRoot, String key) {
				if (jsonRoot.containsKey(key)) {
					final JsonValue value = jsonRoot.get(key);
					if (isEmpty(value)) {
						used.add(key);
					}
				}
			}

			protected String getFromJson(final JsonObject object, final String key) {
				final JsonValue value = object.get(key);
				used.add(key);
				
				if (value instanceof JsonString) {
					return ((JsonString)value).getString();
				}
				
				return (value != null) ? value.toString() : "";
			}

			protected Double getDoubleFromJson(final JsonObject object, final String key) {
				return Double.parseDouble(getFromJson(object, key));
			}

			protected Long getLongFromJson(final JsonObject object, final String key) {
				return Long.parseLong(getFromJson(object, key));
			}
			
			protected void setFromJson(final Position position, final JsonObject object, final String setKey, final String getKey) {
				if (object.containsKey(getKey)) {
					position.set(setKey, getFromJson(object, getKey));
				}
			}

			protected void setDoubleFromJson(final Position position, final JsonObject object, final String setKey, final String getKey) {
				if (object.containsKey(getKey)) {
					position.set(setKey, getDoubleFromJson(object, getKey));
				}
			}

			
		}
		
		public class CSVContextMain extends CSVContext {
			
			@Override
			protected void processExtraData(final Position position, final JsonObject jsonRoot) {
				final String apiVersion = getFromJson(jsonRoot, CSV_API_VERSION);

				if (jsonRoot.containsKey(CSV_SPEED)) {
				    position.setSpeed(convertSpeed(getDoubleFromJson(jsonRoot, CSV_SPEED), "mps"));
				}

				setDoubleFromJson(position, jsonRoot, Position.PREFIX_TEMP +".ambient", CSV_AMBIENT_TEMPERATURE);
				
				setFromJson(position, jsonRoot, TRACCAR_SELECTED_GEAR, CSV_SELECTED_GEAR);
				
				// https://developer.android.com/reference/android/car/VehicleIgnitionState
				final String ignitionState = getFromJson(jsonRoot, CSV_IGNITION_STATE);
				if (StringUtils.isNotBlank(ignitionState)) {
					position.set(Position.KEY_IGNITION, "started".equalsIgnoreCase(ignitionState) || "on".equalsIgnoreCase(ignitionState) );
					position.set(TRACCAR_IGNITION_STATE, ignitionState);
				}
				
				final String chargePortConnected = getFromJson(jsonRoot, CSV_CHARGE_PORT_CONNECTED);
				final String power_mW = getFromJson(jsonRoot, CSV_POWER); // milli watts
				if (StringUtils.isNotEmpty(chargePortConnected)) {
				    position.set(TRACCAR_CHARGE_PORT_CONNECTED, Boolean.parseBoolean(chargePortConnected));
				}
				if (StringUtils.isNotEmpty(power_mW)) {
					// se esta com potencia negativa esta carregando... 
					final boolean isCharging = "true".equals(chargePortConnected) 
							&& StringUtils.isNotEmpty(power_mW) 
							&& power_mW.startsWith("-");
			    	position.set(Position.KEY_CHARGE, isCharging);
				    position.set(TRACCAR_POWER_M_W, power_mW); 
				}

				setDoubleFromJson(position, jsonRoot, TRACCAR_BATTERY_LEVEL_WH, CSV_BATTERY_LEVEL_WH);
				setFromJson(position, jsonRoot, TRACCAR_CSV_APP_VERSION, CSV_APP_VERSION); 

				checkIfNotEmpty(jsonRoot, CSV_ABRP_PACKAGE); // I dont know what should be in this attribute yet
				
				setFromJson(position, jsonRoot, TRACCAR_CHARGING_SESSIONS, CSV_CHARGING_SESSIONS);
				
				processDrivingPoints(position, jsonRoot);
	       
			}

			private void processDrivingPoints(final Position position, final JsonObject jsonRoot) {
				if (jsonRoot.containsKey(CSV_DRIVING_POINTS)) {
					used.add(CSV_DRIVING_POINTS);
					final JsonArray drivingPoints = jsonRoot.getJsonArray(CSV_DRIVING_POINTS);
					drivingPoints.forEach(dp -> {
						new CSVContextDrivingPoint(position, drivingPoints.size() == 1).process((JsonObject)dp);
					});
				}
				
			}
			
		}
		
		public class CSVContextDrivingPoint extends CSVContext {

			private static final String TRACCAR_DRIVING_POINT = "drivingPoint";
			
			private static final String CSV_DRIVING_POINT_POINT_MARKER_TYPE = "point_marker_type";
			private static final String TRACCAR_POINT_MARKER_TYPE = CSV_DRIVING_POINT_POINT_MARKER_TYPE;

			private static final String CSV_DRIVING_POINT_ENERGY_DELTA = "energy_delta"; // Wh
			private static final String TRACCAR_ENERGY_DELTA = "energy_delta"; 
			
			private static final String CSV_DRIVING_POINT_DISTANCE_DELTA = "distance_delta"; // meters

			private final Position rootPosition;
			private final boolean singlePoint;
			
			public CSVContextDrivingPoint(Position rootPosition, final boolean singlePoint) {
				this.rootPosition = rootPosition;
				this.singlePoint = singlePoint;
			}
			
			@Override
			protected void processExtraData(Position position, JsonObject jsonRoot) {
				final Position destination;
				// If have same information with single drivepoint set data on main position object
				// Drive points does not came with speed, in this case will create two positions, one without speed
				// Ignoring and merging data will make history cleaner
				if (singlePoint &&
						(position.getAltitude() == rootPosition.getAltitude()) &&
						(position.getLatitude() == rootPosition.getLatitude()) &&
						(position.getLongitude() == rootPosition.getLongitude()) &&
						(Math.abs(position.getDeviceTime().getTime() - rootPosition.getDeviceTime().getTime()) < 1000)) {
					ignore = true;
					destination = rootPosition;
				} else {
					destination = position;
				}

				// Make easy to identify this drivingPoints entries or combined data
				destination.set(TRACCAR_DRIVING_POINT, true);

				// 1: Start of drive, 2: end of drive (optional)
				setFromJson(destination, jsonRoot, TRACCAR_POINT_MARKER_TYPE, CSV_DRIVING_POINT_POINT_MARKER_TYPE);
				
				setDoubleFromJson(destination, jsonRoot, Position.KEY_DISTANCE, CSV_DRIVING_POINT_DISTANCE_DELTA);
				setDoubleFromJson(destination, jsonRoot, TRACCAR_ENERGY_DELTA, CSV_DRIVING_POINT_ENERGY_DELTA);
			}
			
		}


		public Object getPositions() {
			if (positions.isEmpty()) {
				return null;
			}
			return (positions.size() == 1 ) ? positions.get(0) : positions;
		}
		
		public long getDeviceId() {
			return deviceSession.getDeviceId();
		}

	}
	
	
	private void sendError(Channel channel, String message) {
        sendResponse(channel, HttpResponseStatus.BAD_REQUEST, Unpooled.copiedBuffer(message + "\n\n", StandardCharsets.UTF_8));
	}
	
	private DeviceSession getDeviceSession(Channel channel, SocketAddress remoteAddress, String authID, String uriID) {
        final DeviceSession authDeviceSession = getDeviceSession(channel, remoteAddress, authID);
        if (authDeviceSession != null) {
        	return authDeviceSession;
        }
        final DeviceSession uriDeviceSession = getDeviceSession(channel, remoteAddress, uriID);
        return uriDeviceSession;
	}
	
    @Override
    protected Object decode(Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        final FullHttpRequest request = (FullHttpRequest) msg;
        LOGGER.info("request uri={} method={}", request.uri(), request.method());
        
        if (!request.uri().startsWith(URI_PREFIX)) {
        	LOGGER.error("Invalid uri : {}", request.uri());
            sendError(channel, "Invalid URI");
            return null;
        }
        
        final String uriID = request.uri().substring(URI_PREFIX.length());
        final String authHeader = request.headers().get("Authorization");
        final String authID;
        if (StringUtils.isNotBlank(authHeader) && authHeader.startsWith(AUTH_PREFIX_BASIC)) {
        	authID = authHeader.substring(AUTH_PREFIX_BASIC.length());
        } else {
        	authID = null; 
        }
        if (StringUtils.isBlank(uriID) && StringUtils.isBlank(authID)) {
        	LOGGER.error("Blank ID");
            sendError(channel, "Blank ID");
            return null;
        }

        // Set by the SSL proxy
        final String realIP = request.headers().get("X-Real-IP");
        
        final DeviceSession deviceSession = getDeviceSession(channel, 
        		StringUtils.isNotBlank(realIP) ? new InetSocketAddress(realIP, 1) : remoteAddress, 
        				authID, uriID);
        if (deviceSession == null) {
        	LOGGER.error("Invalid ID : uri={} auth={}", uriID, authID);
            sendError(channel, "Invalid ID");
            return null;
        }

        final QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        final Map<String, List<String>> params = decoder.parameters();
        final String contentData = request.content().toString(StandardCharsets.US_ASCII);
        
        if (!params.isEmpty()) {
            LOGGER.info("params={}", params);
        }
        
        final CSVProcessor ctx = new CSVProcessor(deviceSession);
        ctx.process(contentData);

        String response = null;
        for (Command command : getCommandsManager().readQueuedCommands(ctx.getDeviceId(), 1)) {
            response = command.getString(Command.KEY_DATA);
        }
        
        if (response != null) {
            sendResponse(channel, HttpResponseStatus.OK, Unpooled.copiedBuffer(response, StandardCharsets.UTF_8));
        } else {
            sendResponse(channel, HttpResponseStatus.OK);
        }
        
        return ctx.getPositions();
    }


	@Override
    protected void sendQueuedCommands(Channel channel, SocketAddress remoteAddress, long deviceId) {
    }

}
