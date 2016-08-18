package ru.semiot.platform.drivers.lamp;

import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceProperties;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public abstract class Keys extends DeviceProperties {

  public static final String DRIVER_PID
      = "ru.semiot.platform.drivers.lamp";
  public static final String COAP_ENDPOINT = "ru.semiot.platform.drivers.COAPEndpoint";
  public static final String COAP_RESOURCE = "/led1";
  public static final String PROCESS_LIGHT_PARAMETER_LUMEN = "ru.semiot.platform.drivers.lamp.lumen";
}
