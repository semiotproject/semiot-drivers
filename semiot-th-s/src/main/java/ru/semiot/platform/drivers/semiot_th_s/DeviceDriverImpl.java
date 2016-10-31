package ru.semiot.platform.drivers.semiot_th_s;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Configuration;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriver;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriverManager;
import ru.semiot.platform.deviceproxyservice.api.drivers.DriverInformation;

import java.net.URI;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

public class DeviceDriverImpl implements DeviceDriver, ManagedService {

  private static final Logger logger = LoggerFactory.getLogger(DeviceDriverImpl.class);
  private static final String DRIVER_NAME = "SEMIOT TH-S Driver";
  private static final String JSON_KEY_SYSTEM_INFO = "systemInfo";
  private static final String JSON_KEY_IDENTIFIER = "identifier";
  private static final String JSON_KEY_LABEL = "label";
  private static final String JSON_KEY_LOCATION = "location";
  private static final String JSON_KEY_VALUE = "value";
  private static final String JSON_KEY_TYPE = "@type";

  private final Configuration configuration = new Configuration();
  private final DriverInformation info
      = new DriverInformation(Keys.DRIVER_PID,
      URI.create("https://raw.githubusercontent.com/semiotproject/semiot-drivers/"
          + "master/semiot-th-s/"
          + "src/main/resources/ru/semiot/platform/drivers/semiot_th_s/prototype.ttl#SEMIOTTHSDevice"));
  private final Map<String, SEMIOTTHSDevice> devicesMap = Collections.synchronizedMap(new HashMap<>());

  private volatile DeviceDriverManager deviceManager;
  private CoapServer coapServer;

  public void start() {
    logger.info("{} started!", DRIVER_NAME);
    deviceManager.registerDriver(info);

    int endpointPort = configuration.getAsInteger(Keys.COAP_ENDPOINT_PORT);
    coapServer = new CoapServer(endpointPort);
    coapServer.add(new CoapResource("test") {

      @Override
      public void handlePOST(CoapExchange exchange) {
        logger.debug("Received request!");
        String payload = exchange.getRequestText();

        if (payload != null) {
          SEMIOTTHSDevice device = parseDeviceInformation(payload);
          if (devicesMap.containsKey(device.getId())) {
            //TODO: Update device info
          } else {
            deviceManager.registerDevice(info, device);
            devicesMap.put(device.getId(), device);
          }

          SEMIOTTHSObservation observation = parseObservation(payload);

          deviceManager.registerObservation(device, observation);
        }
      }
    });

    coapServer.start();
  }

  public void stop() {
    if (coapServer != null) {
      coapServer.stop();
      coapServer.destroy();
    }
    logger.info("{} stopped!", DRIVER_NAME);
  }

  public void registerDevice(SEMIOTTHSDevice device) {
    if (!devicesMap.containsKey(device.getId())) {
      devicesMap.put(device.getId(), device);
      deviceManager.registerDevice(info, device);
    }
  }

  public void publishNewObservation(SEMIOTTHSObservation observation) {
    String deviceId = observation.getProperty(Keys.DEVICE_ID);
    deviceManager.registerObservation(devicesMap.get(deviceId), observation);
  }

  @Override
  public String getDriverName() {
    return DRIVER_NAME;
  }

  @Override
  public void updated(Dictionary properties) throws ConfigurationException {
    synchronized (this) {
      if (properties != null) {
        if (!configuration.isConfigured()) {
          logger.debug("Received new configuration");
          configuration.putAll(properties);
          configuration.setConfigured();
          logger.info("Saved new configuration");
        } else {
          logger.debug("Is already configured. Skipping.");
        }
      } else {
        logger.debug("Configuration is empty. Skipping.");
      }
    }
  }

  private SEMIOTTHSDevice parseDeviceInformation(String payload) {
    JsonObject object = Json.parse(payload).asObject();
    JsonObject systemInfo = object.get(JSON_KEY_SYSTEM_INFO).asObject();

    String id = systemInfo.get(JSON_KEY_IDENTIFIER).asString();
    String label = systemInfo.get(JSON_KEY_LABEL).asString();
    String room = systemInfo.get(JSON_KEY_LOCATION).asObject().get(JSON_KEY_LABEL).asString();

    logger.debug("id: {}, label: {}, room: {}", id, label, room);

    return new SEMIOTTHSDevice(label, id, room);
  }

  private SEMIOTTHSObservation parseObservation(String payload) {
    JsonObject object = Json.parse(payload).asObject();
    String deviceId = object.get(JSON_KEY_SYSTEM_INFO).asObject().get(JSON_KEY_IDENTIFIER).asString();
    String type = object.get(JSON_KEY_TYPE).asString();
    String value = object.get(JSON_KEY_VALUE).asString();
    String sensorId;

    if (type.equalsIgnoreCase("doc:TemperatureValue")) {
      type = SEMIOTTHSObservation.TEMPERATURE_TYPE;
      sensorId = SEMIOTTHSObservation.TEMPERATURE_TEMPLATE_SENSOR.replace("${SYSTEM_ID}", deviceId);
    } else {
      type = SEMIOTTHSObservation.HUMIDITY_TYPE;
      sensorId = SEMIOTTHSObservation.HUMIDITY_TEMPLATE_SENSOR.replace("${SYSTEM_ID}", deviceId);
    }

    logger.debug("deviceId: {}, sensorId: {}, value: {}", deviceId, sensorId, value);

    return new SEMIOTTHSObservation(deviceId, sensorId, Long.toString(System.currentTimeMillis()),
        value, type);
  }

}
