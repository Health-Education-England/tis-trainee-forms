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

import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.APPROVED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.trainee.forms.dto.LtftAdminSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.LftfStatusInfoDetailDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.service.LtftService;

/**
 * A controller for admin facing Less Than Full-time (LTFT) endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ltft")
@XRayEnabled
public class AdminLtftResource {

  private final LtftService service;

  public AdminLtftResource(LtftService service) {
    this.service = service;
  }

  /**
   * Get a count of LTFT applications associated with the admin's local office.
   *
   * @param states The states to include in the count.
   * @return The number of LTFT applications found meeting the criteria.
   */
  @GetMapping("/count")
  public ResponseEntity<String> getAdminLtftCount(
      @RequestParam(value = "status", required = false) Set<LifecycleState> states) {
    long count = service.getAdminLtftCount(states);
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(String.valueOf(count));
  }

  /**
   * Get the LTFT application summaries associated with the admin's local office.
   *
   * @param pageable The desired paging and sorting details, defaults to all records sorted by
   *                 submission date.
   * @return A page of LTFT summaries meeting the criteria.
   */
  @GetMapping
  ResponseEntity<PagedModel<LtftAdminSummaryDto>> getLtftAdminSummaries(
      @RequestParam(value = "status", required = false) Set<LifecycleState> states,
      @PageableDefault(size = Integer.MAX_VALUE, sort = "content.change.startDate",
          direction = Direction.ASC)
      Pageable pageable) {
    Page<LtftAdminSummaryDto> page = service.getAdminLtftSummaries(states, pageable);
    return ResponseEntity.ok(new PagedModel<>(page));
  }

  /**
   * Get the details of a form with a particular ID associated with the admin's local office.
   *
   * @param id The ID of the form.
   * @return The found form details, empty if not found.
   */
  @GetMapping("/{id}")
  ResponseEntity<LtftFormDto> getLtftAdminDetail(@PathVariable UUID id) {
    Optional<LtftFormDto> formDetail = service.getAdminLtftDetail(id);
    return ResponseEntity.of(formDetail);
  }

  /**
   * Approve the form with the given ID, must be associated with the user's local office.
   *
   * @param id The ID of the form to approve.
   * @return The approved form.
   * @throws MethodArgumentNotValidException When the state transition was not valid.
   */
  @PutMapping("/{id}/approve")
  ResponseEntity<LtftFormDto> approveLtft(@PathVariable UUID id)
      throws MethodArgumentNotValidException {
    Optional<LtftFormDto> form = service.updateStatusAsAdmin(id, APPROVED, null);
    return ResponseEntity.of(form);
  }

  /**
   * Unsubmit the form with the given ID, must be associated with the user's local office.
   *
   * @param id The ID of the form to unsubmit.
   * @return The unsubmitted form.
   * @throws MethodArgumentNotValidException When the state transition was not valid.
   */
  @PutMapping("/{id}/unsubmit")
  ResponseEntity<LtftFormDto> unsubmitLtft(@PathVariable UUID id, @RequestBody
      LftfStatusInfoDetailDto detail)
      throws MethodArgumentNotValidException {
    Optional<LtftFormDto> form = service.updateStatusAsAdmin(id, UNSUBMITTED, detail);
    return ResponseEntity.of(form);
  }
}
