package ru.semiot.platform.drivers.dht22;

import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceProperties;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public abstract class Keys extends DeviceProperties{

  public static final String DRIVER_PID = "ru.semiot.platform.drivers.dht22";
  public static final String COAP_ENDPOINT = "ru.semiot.platform.drivers.COAPEndpoint";
  public static final String POLLING_INTERVAL = "ru.semiot.platform.drivers.pollingInterval";

  public static final String OBSERVATION_VALUE = "ru.semiot.platform.drivers.dht22.observation.value";
  public static final String OBSERVATION_TYPE = "ru.semiot.platform.drivers.dht22.observation.type";
  public static final String ROOM = "ru.semiot.platform.drivers.dht22.room";

}
