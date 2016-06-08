package ru.semiot.drivers.regulator.simulator;

import java.io.IOException;
import org.eclipse.californium.core.CoapClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Device;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class Regulator extends Device {

  private static final Logger logger = LoggerFactory.getLogger(Regulator.class);
  private static final String TEMPLATE_PATH = "/ru/semiot/drivers/regulator/simulator/description.ttl";
  private static RDFTemplate TEMPLATE_DESCRIPTION;
  private CoapClient client;

  private double pressure;

  public void setPressure(double pressure) {
    this.pressure = pressure;
    try {
      client.put(Double.toString(pressure), 0);
    } catch (RuntimeException ex) {
      logger.error("Bad resource! Can't execute request! URI is {}.Exception message is {}", client.getURI(), ex.getMessage());
    }

  }

  public double getPressure() {
    return pressure;
  }

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

  public Regulator(String id, String uri, String building_id) {
    super(id);
    getProperties().put(Keys.BUILDING_ID, building_id);
    client = new CoapClient(uri);
    try {
      this.pressure = Double.parseDouble(client.get().getResponseText());
    } catch (NumberFormatException | NullPointerException ex) {
      logger.error("Bad response! Can't parse response value. Exception message is {}", ex.getMessage());
    } catch (RuntimeException ex) {
      logger.error("Bad resource! Can't execute request! URI is {}.Exception message is {}", client.getURI(), ex.getMessage());
    }
  }

  @Override
  public RDFTemplate getRDFTemplate() {
    return TEMPLATE_DESCRIPTION;
  }

}
