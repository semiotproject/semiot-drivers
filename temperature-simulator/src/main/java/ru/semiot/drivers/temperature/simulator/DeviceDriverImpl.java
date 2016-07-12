package ru.semiot.drivers.temperature.simulator;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.WebLink;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Configuration;
import ru.semiot.platform.deviceproxyservice.api.drivers.Device;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriver;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriverManager;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceProperties;
import ru.semiot.platform.deviceproxyservice.api.drivers.DriverInformation;
import ru.semiot.platform.deviceproxyservice.api.drivers.Observation;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class DeviceDriverImpl implements DeviceDriver, ManagedService {

  private static final Logger logger = LoggerFactory.getLogger(DeviceDriverImpl.class);
  private static final String DRIVER_NAME = "Temperature Simulator Driver";
  private final Configuration configuration = new Configuration();
  private final DriverInformation info
      = new DriverInformation(Keys.DRIVER_PID,
          URI.create("https://raw.githubusercontent.com/semiotproject/semiot-drivers/"
              + "master/temperature-simulator/"
              + "src/main/resources/ru/semiot/drivers/temperature/simulator/prototype.ttl#Mercury270"));
  private volatile DeviceDriverManager manager;
  private Configuration commonConfiguration;
  private CoapClient client;
  private final Map<String, Device> devicesMap = Collections.synchronizedMap(new HashMap<>());
  private List<CoapObserveRelation> relations = new ArrayList<>();

  public void start() {
    logger.debug("{} is starting!", DRIVER_NAME);
    manager.registerDriver(info);
    logger.debug("Try to get descrioption of devices");
    client.setURI(commonConfiguration.getAsString(Keys.COAP_ENDPOINT));
    //client.setTimeout(0);
    List<String> buildings = new ArrayList<>();
    Set<WebLink> discover = client.discover();
    for (WebLink link : discover) {
      if (link.getURI().matches("/\\d+" + Keys.SIMULATOR_DESCRIPTION_POSTFIX)) {
        buildings.add(link.getURI().substring(1, link.getURI().lastIndexOf('/')));
      }
    }

    int count = 0;
    List<TemperatureDevice> devices = new ArrayList<>();
    for (String building : buildings) {
      try {
        client.setURI(commonConfiguration.getAsString(Keys.COAP_ENDPOINT) + "/" + building
            + Keys.SIMULATOR_DESCRIPTION_POSTFIX);
        String desq = client.get().getResponseText();
        if (desq == null) {
          logger.error("Can't get description! Exiting...");
          stop();
          return;
        }
        devices.addAll(DriverUtils.getDevices(new JSONObject(desq)));
        
        logger.debug("{} devices are registered for building {}!", devices.size(), building);

      } catch (JSONException ex) {
        logger.error("Bad response format! Can't read description! Exception message is {}", ex.getMessage());
        return;
      }
    }
    
    for (Device dev : devices) {
      registerDevice(dev);
    }
    logger.debug("Yeah. {} devices are registered", count);

    /* logger.debug("Subscribe for new observations");
    for (String building : buildings) {
      client.setURI(commonConfiguration.getAsString(Keys.COAP_ENDPOINT) + "/" + building
          + Keys.SIMULATOR_OBSERVATION_POSTFIX);
      relations.add(client.observe(new CoapHandler() {
        @Override
        public void onLoad(CoapResponse response) {
          try {

            //List<TemperatureObservation> obs = DriverUtils.getObservations(new JSONArray(response.getResponseText()));
            //for (TemperatureObservation o : obs) {
            //publishNewObservation(o);
            //}
            DriverUtils.getAndPublishObservations(new JSONArray(response.getResponseText()), manager, devicesMap);
          } catch (JSONException ex) {
            logger.error("Bad response format! Can't read observations! Exception message is {}", ex.getMessage());
          }
        }

        @Override
        public void onError() {
          logger.error("Something went wrong! Can't get observation");
        }
      }));
    }*/

    logger.info("{} started!", DRIVER_NAME);
  }

  public void stop() {
    logger.debug("{} is stopping!", DRIVER_NAME);
    logger.debug("Try to shutdown CoapClient");
    for (CoapObserveRelation relation : relations) {
      relation.reactiveCancel();
    }
    //client.shutdown();
    devicesMap.clear();
    logger.info("{} stopped!", DRIVER_NAME);
  }

  public void registerDevice(Device device) {
    devicesMap.put(device.getId(), device);
    manager.registerDevice(info, device);
  }

  public void publishNewObservation(Observation observation) {
    String deviceId = observation.getProperty(DeviceProperties.DEVICE_ID);
    manager.registerObservation(devicesMap.get(deviceId), observation);
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
          logger.debug("Configuration got");
          try {
            configuration.putAll(properties);
            commonConfiguration = getCommonConfiguration(configuration);
            configuration.setConfigured();
            logger.info("Received configuration is correct!");
          } catch (ConfigurationException ex) {
            configuration.clear();
            throw ex;
          }
        } else {
          logger.warn("Is already configured! Skipping.");
        }
      } else {
        logger.debug("Configuration is empty. Skipping.");
      }
    }
  }

  private Configuration getCommonConfiguration(Configuration cfg) throws ConfigurationException {
    logger.debug("Try to get common configuration");
    Configuration config = new Configuration();
    try {
      String uri = cfg.getAsString(Keys.COAP_ENDPOINT);
      if (uri == null) {
        logger.error("Bad common configuration! Field '{}' is null!", Keys.COAP_ENDPOINT);
        throw new ConfigurationException(Keys.COAP_ENDPOINT,
            "Bad common configuration. Field is null");
      }
      if (uri.endsWith("/")) {
        uri = uri.substring(0, uri.length() - 1);
      }
      client = new CoapClient(uri);
      if (!client.ping()) {
        logger.error("Bad common configuration! Cannot connect with uri '{}'" + uri);
        throw new ConfigurationException(Keys.COAP_ENDPOINT,
            "Bad common configuration. Cannot connect with uri " + uri);
      }
      config.put(Keys.COAP_ENDPOINT, uri);
    } catch (java.lang.NullPointerException ex) {
      logger.error("Bad common configuration! Can not extract fields");
      throw new ConfigurationException("Common property", "Can not extract fields", ex);
    }
    return config;
  }

}
