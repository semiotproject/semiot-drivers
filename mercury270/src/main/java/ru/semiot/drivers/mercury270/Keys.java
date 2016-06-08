package ru.semiot.drivers.mercury270;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public interface Keys {
  public static final String DRIVER_PID = "ru.semiot.drivers.mercury270";
  public static final String COAP_ENDPOINT = "ru.semiot.drivers.COAPEndpoint";
  public static final String PULLING_INTERVAL = "ru.semiot.drivers.pullingInterval";

  public static final String DEVICE_TICK = "/tick";
  public static final String DEVICE_MAC = "/mac";
  public static final String DEVICE_MODEL = "/model";
  public static final String DEVICE_SERIAL = "/serial";
  public static final String DEVICE_CORE = "/.well-known/core";

  public static final String OBSERVATION_VALUE = "ru.semiot.drivers.mercury270.observation.value";
  public static final String OBSERVATION_TYPE = "ru.semiot.drivers.mercury270.observation.type";
}
