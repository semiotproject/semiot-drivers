package ru.semiot.drivers.regulator.relay;

import org.eclipse.californium.core.CoapClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Device;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

import java.io.IOException;

public class Regulator extends Device {

  private static final Logger logger = LoggerFactory.getLogger(Regulator.class);
  private static final String TEMPLATE_PATH = "/ru/semiot/drivers/regulator/relay/description.ttl";
  private static RDFTemplate TEMPLATE_DESCRIPTION;
  private static final String TEMPLATE_MSG = "{\"@context\":\"http://external/doc#\",\"@type\":\"${VAL}\"}";
  private static final String TURN_ON_ACTION = "TurnOnAction";
  private static final String TURN_OFF_ACTION = "TurnOffAction";
  private CoapClient client;

  private boolean state = false;

  static {
    try {
      TEMPLATE_DESCRIPTION = new RDFTemplate("description",
          Regulator.class.getResourceAsStream(TEMPLATE_PATH));
    } catch (IOException ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  public void shutdown() {
    client.shutdown();
  }
  
  public boolean getState() {
    return state;
  }
  
  public void setState(boolean state) {
    this.state = state;
    try {
      logger.info(TEMPLATE_MSG.replace("${VAL}", state ? TURN_ON_ACTION : TURN_OFF_ACTION));
      client.put(TEMPLATE_MSG.replace("${VAL}", state ? TURN_ON_ACTION : TURN_OFF_ACTION), 0);
      
    } catch (RuntimeException ex) {
      logger.error("Bad resource! Can't execute request! URI is {}.Exception message is {}", client.getURI(), ex.getMessage());
    }

  }

  public Regulator(String id, String uri) {
    super(id);
    logger.info(uri);
    client = new CoapClient(uri);
    try {
      String state = client.get().getResponseText();
      logger.info(state);
      this.state = state.indexOf("TurnOn")!=-1 ? true : false;
    } catch (RuntimeException ex) {
      logger.error("Bad resource! Can't execute request! URI is {}.Exception message is {}", client.getURI(), ex.getMessage());
    }
  }

  @Override
  public RDFTemplate getRDFTemplate() {
    return TEMPLATE_DESCRIPTION;
  }

}
