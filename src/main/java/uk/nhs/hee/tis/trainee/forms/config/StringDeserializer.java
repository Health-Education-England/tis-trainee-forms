package uk.nhs.hee.tis.trainee.forms.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class StringDeserializer extends JsonDeserializer<String> {

  @Override
  public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    return p.getValueAsString().trim();
  }
}