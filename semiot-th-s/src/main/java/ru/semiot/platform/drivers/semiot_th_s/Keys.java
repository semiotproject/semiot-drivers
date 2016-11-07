package ru.semiot.platform.drivers.semiot_th_s;

import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceProperties;

public abstract class Keys extends DeviceProperties{

  public static final String DRIVER_PID = "ru.semiot.platform.drivers.semiot_th_s";
  public static final String COAP_ENDPOINT_PORT = "ru.semiot.platform.drivers.coapEndpointPort";
  public static final String LISTEN_ON_PATH = "ru.semiot.platform.drivers.listenPath";

  public static final String OBSERVATION_VALUE = "ru.semiot.platform.drivers.semiot_th_s.observation.value";
  public static final String OBSERVATION_TYPE = "ru.semiot.platform.drivers.semiot_th_s.observation.type";

  public static final String DEVICE_ROOM = "ru.semiot.platform.drivers.semiot_th_s.room";
  public static final String DEVICE_LABEL = "ru.semiot.platform.drivers.semiot_th_s.label";

}
