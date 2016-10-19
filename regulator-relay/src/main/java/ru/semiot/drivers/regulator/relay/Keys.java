package ru.semiot.drivers.regulator.relay;

import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceProperties;

public abstract class Keys extends DeviceProperties {

  public static final String DRIVER_PID = "ru.semiot.drivers.regulator-relay";
  public static final String COAP_ENDPOINT = "ru.semiot.drivers.COAPEndpoint";
  public static final String COAP_CLIENT_PORT = "ru.semiot.drivers.COAPClientPort";
  public static final String COAP_RESOURCE_RELAY = "/relay";

}
