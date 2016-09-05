package ru.semiot.platform.drivers.lamp;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.WebLink;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Command;
import ru.semiot.platform.deviceproxyservice.api.drivers.CommandExecutionException;
import ru.semiot.platform.deviceproxyservice.api.drivers.CommandResult;
import ru.semiot.platform.deviceproxyservice.api.drivers.Configuration;
import ru.semiot.platform.deviceproxyservice.api.drivers.ControllableDeviceDriver;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriverManager;
import ru.semiot.platform.deviceproxyservice.api.drivers.DriverInformation;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class DeviceDriverImpl implements ControllableDeviceDriver, ManagedService {

  private static final Logger logger = LoggerFactory.getLogger(DeviceDriverImpl.class);
  private static final String PROTOTYPE_URI_PREFIX
      = "https://raw.githubusercontent.com/semiotproject/semiot-drivers/"
      + "master/lamp/"
      + "src/main/resources/ru/semiot/platform/drivers/lamp/prototype.ttl#";
  private static final String DRIVER_NAME = "Lamp Driver";
  private static final int FNV_32_INIT = 0x811c9dc5;
  private static final int FNV_32_PRIME = 0x01000193;
  private static final String PROCESS_LIGHT = "light";
  private static final String COMMAND_LIGHT_STOP = "light-stopcommand";
  private static final String COMMAND_LIGHT_START = "light-startcommand";
  private final Configuration configuration = new Configuration();
  private final DriverInformation info
      = new DriverInformation(Keys.DRIVER_PID, URI.create(PROTOTYPE_URI_PREFIX + "Lamp"));
  private final Map<String, LampDevice> lamps = new HashMap<>();

  private volatile DeviceDriverManager manager;
  private Configuration commonConfiguration;
  private RDFTemplate TEMPLATE_COMMAND_LIGHT_STOP;
  private RDFTemplate TEMPLATE_COMMAND_LIGHT_START;


  public void start() {
    logger.debug("{} is starting!", DRIVER_NAME);
    loadRDFTemplates();
    manager.registerDriver(info);

    Set<WebLink> discover = new CoapClient(commonConfiguration.getAsString(Keys.COAP_ENDPOINT))
        .discover();
    String index;
    String URI = commonConfiguration.getAsString(Keys.COAP_ENDPOINT);
    String id;
    for (WebLink link : discover) {
      logger.debug("Link is {}", link.getURI());
      if (link.getURI().matches("/led\\d+")) {
        index = link.getURI();
        id = hash(Keys.DRIVER_PID, index);
        LampDevice lamp = new LampDevice(id, URI + index);
        lamps.put(id, lamp);
        manager.registerDevice(info, lamp);

      }
    }

    for (String _id : lamps.keySet()) {
      Command cmd;
      RDFTemplate tmpl;
      if (lamps.get(_id).getIsOn()) {
        cmd = new Command(PROCESS_LIGHT, COMMAND_LIGHT_START);
        cmd.add(Keys.PROCESS_ID, PROCESS_LIGHT);
        cmd.add(Keys.DEVICE_ID, lamps.get(_id).getId());
        cmd.add(Keys.PROCESS_LIGHT_PARAMETER_LUMEN, lamps.get(_id).getBrightness());
        tmpl = getRDFTemplate(COMMAND_LIGHT_START);
      } else {
        cmd = new Command(PROCESS_LIGHT, COMMAND_LIGHT_STOP);
        cmd.add(Keys.PROCESS_ID, PROCESS_LIGHT);
        cmd.add(Keys.DEVICE_ID, lamps.get(_id).getId());
        tmpl = getRDFTemplate(COMMAND_LIGHT_STOP);
      }

      manager.registerCommand(lamps.get(_id), new CommandResult(
          cmd,
          tmpl,
          ZonedDateTime.now()));
    }
    logger.info("{} started!", DRIVER_NAME);
  }

  public void stop() {
    logger.debug("{} are stopping!", DRIVER_NAME);
    for (LampDevice lamp : lamps.values()) {
      lamp.shutdown();
    }
    lamps.clear();
    logger.info("{} stopped!", DRIVER_NAME);
  }

  @Override
  public CommandResult executeCommand(Command command) throws CommandExecutionException {
    try {
      if (lamps.containsKey(command.get(Keys.DEVICE_ID))) {
        LampDevice device = lamps.get(command.get(Keys.DEVICE_ID));
        String commandId = command.get(Keys.COMMAND_ID);
        String processId = command.get(Keys.PROCESS_ID);
        synchronized (device) {
          if (processId.equals(PROCESS_LIGHT)) {
            if (commandId.equals(COMMAND_LIGHT_STOP)) {
              device.setIsOn(false);
              logger.debug("[ID={}] Turned off the light!", device.getId());
            } else if (commandId.equals(COMMAND_LIGHT_START)) {
              device.setIsOn(true);
              device.setBrightness(command.getAsInteger(
                  Keys.PROCESS_LIGHT_PARAMETER_LUMEN));

              logger.debug("[ID={}] Turned on the light! Lumen: {}",
                  device.getId(), device.getBrightness());
            } else {
              throw CommandExecutionException.badCommand(
                  "Command [%s] is not supported!", commandId);
            }
          } else {
            throw CommandExecutionException.badCommand("Process [%s] is not supported!", processId);
          }
        }

        CommandResult result = new CommandResult(
            command, getRDFTemplate(commandId), ZonedDateTime.now());
        manager.registerCommand(device, result);

        return result;
      } else {
        throw CommandExecutionException.systemNotFound();
      }
    } catch (Throwable e) {
      if (e instanceof CommandExecutionException) {
        throw e;
      } else {
        throw CommandExecutionException.badCommand(e);
      }
    }
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
      /*if (!new CoapClient(uri).ping()) {
        logger.error("Bad repeatable configuration! Cannot connect with uri '{}'", uri);
        throw new ConfigurationException(Keys.COAP_ENDPOINT,
            "Bad common configuration. Cannot connect with uri " + uri);
      }*/
      config.put(Keys.COAP_ENDPOINT, uri);
    } catch (Throwable ex) {
      logger.error("Bad common configuration! Can not extract fields");
      throw new ConfigurationException("Common property", "Can not extract fields", ex);
    }
    return config;
  }

  @Override
  public RDFTemplate getRDFTemplate(String id) {
    switch (id) {
      case COMMAND_LIGHT_START:
        return TEMPLATE_COMMAND_LIGHT_START;
      case COMMAND_LIGHT_STOP:
        return TEMPLATE_COMMAND_LIGHT_STOP;
      default:
        throw new IllegalArgumentException();
    }
  }

  private void loadRDFTemplates() {
    try {
      TEMPLATE_COMMAND_LIGHT_STOP = new RDFTemplate(COMMAND_LIGHT_STOP, this.getClass()
          .getResourceAsStream("/ru/semiot/platform/drivers/lamp/light-stopcommand.ttl"));
      TEMPLATE_COMMAND_LIGHT_START = new RDFTemplate(COMMAND_LIGHT_START, this.getClass()
          .getResourceAsStream("/ru/semiot/platform/drivers/lamp/light-startcommand.ttl"));
    } catch (Throwable ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  private static String hash(String prefix, String id) {
    String name = prefix + id;
    int h = FNV_32_INIT;
    final int len = name.length();
    for (int i = 0; i < len; i++) {
      h ^= name.charAt(i);
      h *= FNV_32_PRIME;
    }
    long longHash = h & 0xffffffffl;
    return String.valueOf(longHash);
  }
}
