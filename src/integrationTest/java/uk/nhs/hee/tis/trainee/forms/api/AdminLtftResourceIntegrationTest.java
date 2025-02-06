/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.hee.tis.trainee.forms.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class AdminLtftResourceIntegrationTest {

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MongoTemplate template;

  @AfterEach
  void tearDown() {
    template.findAllAndRemove(new Query(), LtftForm.class);
  }

  @Test
  void shouldReturnErrorWhenInvalidStatusFilter() throws Exception {
    mockMvc.perform(get("/api/admin/ltft/count")
            .param("status", "INVALID_FILTER"))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldCountZeroWhenNoLtfts(String statusFilter) throws Exception {
    mockMvc.perform(get("/api/admin/ltft/count")
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_PLAIN))
        .andExpect(content().string("0"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldCountAllLtftsWhenNoStatusFilter(String statusFilter) throws Exception {
    List<LtftForm> ltfts = Arrays.stream(LifecycleState.values())
        .map(s -> {
          LtftForm ltft = new LtftForm();
          ltft.setStatus(s);
          return ltft;
        })
        .toList();
    template.insertAll(ltfts);

    LtftForm ltft = new LtftForm();
    ltft.setStatus(LifecycleState.SUBMITTED);
    template.insert(ltft);

    mockMvc.perform(get("/api/admin/ltft/count")
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_PLAIN))
        .andExpect(content().string(String.valueOf(LifecycleState.values().length + 1)));
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldCountMatchingLtftsWhenHasStatusFilter(LifecycleState status) throws Exception {
    List<LtftForm> ltfts = Arrays.stream(LifecycleState.values())
        .map(s -> {
          LtftForm ltft = new LtftForm();
          ltft.setStatus(s);
          return ltft;
        })
        .toList();
    template.insertAll(ltfts);

    mockMvc.perform(get("/api/admin/ltft/count")
            .param("status", status.toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_PLAIN))
        .andExpect(content().string(String.valueOf(1)));
  }

  @Test
  void shouldCountMatchingLtftsWhenMultipleStatusFilters() throws Exception {
    List<LtftForm> ltfts = Arrays.stream(LifecycleState.values())
        .map(s -> {
          LtftForm ltft = new LtftForm();
          ltft.setStatus(s);
          return ltft;
        })
        .toList();
    template.insertAll(ltfts);

    LtftForm ltft = new LtftForm();
    ltft.setStatus(LifecycleState.SUBMITTED);
    template.insert(ltft);

    String statusFilter = "%s,%s".formatted(LifecycleState.SUBMITTED, LifecycleState.UNSUBMITTED);
    mockMvc.perform(get("/api/admin/ltft/count")
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_PLAIN))
        .andExpect(content().string(String.valueOf(3)));
  }
}
