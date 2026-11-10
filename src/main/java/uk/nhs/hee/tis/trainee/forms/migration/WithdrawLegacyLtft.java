/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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

package uk.nhs.hee.tis.trainee.forms.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.service.LtftService;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This form was submitted to validate that there was no problem with the user's account/data.
 */
@Slf4j
@ChangeUnit(id = "withdrawLegacyLtft", order = "013")
public class WithdrawLegacyLtft {

  private final MongoTemplate mongoTemplate;
  private final LtftService service;

  private static final String REASON = "Withdrawn during pilot";
  private static final String MESSAGE = "This application was submitted erroneously, or in " +
      "duplication, during the LTFT onboarding and transition from existing regional processes. " +
      "Please note there is no action to take.";
  private static final List<String> FORM_REFS = List.of(
      "ltft_281057_001", "ltft_248325_001", "ltft_190825_001", "ltft_67161_001", "ltft_735_001",
      "ltft_247226_001", "ltft_332977_001", "ltft_332977_002", "ltft_6409_002", "ltft_4404_001",
      "ltft_249144_001", "ltft_115353_001", "ltft_259803_001", "ltft_115309_001", "ltft_18682_001",
      "ltft_267901_001", "ltft_11863_002", "ltft_11863_001", "ltft_277719_001", "ltft_91941_001",
      "ltft_11863_003", "ltft_277719_002", "ltft_249320_001", "ltft_247746_001", "ltft_258935_001",
      "ltft_111363_001", "ltft_249545_001", "ltft_248701_001", "ltft_307664_001", "ltft_275604_001",
      "ltft_308204_001", "ltft_293473_001", "ltft_316035_003", "ltft_272518_001", "ltft_250163_001",
      "ltft_308902_001", "ltft_330117_001", "ltft_59407_001", "ltft_306694_001", "ltft_265232_001",
      "ltft_260329_001", "ltft_322706_001", "ltft_341393_001", "ltft_254190_001", "ltft_341393_002",
      "ltft_302149_001", "ltft_325908_001", "ltft_291540_001", "ltft_295087_001", "ltft_315231_001",
      "ltft_308868_001", "ltft_341391_001", "ltft_325917_001", "ltft_325675_001", "ltft_301179_001",
      "ltft_291635_001", "ltft_294910_001", "ltft_299930_001", "ltft_284734_001", "ltft_341389_001",
      "ltft_330502_001", "ltft_282035_001", "ltft_111304_001", "ltft_340499_001", "ltft_296202_001",
      "ltft_259673_001", "ltft_77465_001", "ltft_301009_001", "ltft_319729_001", "ltft_258804_001",
      "ltft_284724_001", "ltft_318880_001", "ltft_281279_001", "ltft_295249_001", "ltft_205573_001",
      "ltft_325505_001", "ltft_322284_001", "ltft_263957_001", "ltft_270987_001", "ltft_294044_001",
      "ltft_325674_001", "ltft_315216_001", "ltft_291712_002", "ltft_291712_001", "ltft_291712_003",
      "ltft_315216_002", "ltft_281245_001", "ltft_283581_001", "ltft_293387_001", "ltft_309989_001",
      "ltft_281220_001", "ltft_270908_001", "ltft_276625_001", "ltft_285570_001", "ltft_341352_001",
      "ltft_308535_001", "ltft_308535_002", "ltft_307564_001", "ltft_284532_001", "ltft_273909_001",
      "ltft_281105_001", "ltft_284726_002", "ltft_293885_001", "ltft_284437_001", "ltft_314747_001",
      "ltft_281395_001", "ltft_271898_001", "ltft_19367_001", "ltft_249449_001", "ltft_22771_001",
      "ltft_270735_001", "ltft_53051_001", "ltft_45691_001", "ltft_317135_001", "ltft_305641_001",
      "ltft_298956_001", "ltft_276705_001", "ltft_276704_001", "ltft_259803_002", "ltft_70827_001",
      "ltft_249635_001", "ltft_297657_001", "ltft_329800_001", "ltft_329807_001", "ltft_276145_001",
      "ltft_259997_001", "ltft_59682_001", "ltft_260437_001", "ltft_275813_001", "ltft_257461_001",
      "ltft_116622_001", "ltft_275801_001", "ltft_71871_001", "ltft_307759_001", "ltft_63561_001",
      "ltft_248215_001", "ltft_274326_001", "ltft_248791_001", "ltft_249876_001", "ltft_135302_001",
      "ltft_300118_001", "ltft_281411_001", "ltft_284507_001", "ltft_265627_001", "ltft_294680_001",
      "ltft_295562_001", "ltft_357344_001", "ltft_357338_001", "ltft_313323_001", "ltft_308174_001",
      "ltft_281130_001", "ltft_286755_001", "ltft_259467_001", "ltft_270879_001", "ltft_315466_001",
      "ltft_319047_004", "ltft_319047_005", "ltft_4576_001", "ltft_11830_001", "ltft_249617_001",
      "ltft_57454_001", "ltft_269089_001", "ltft_12789_001", "ltft_12158_001", "ltft_11695_001",
      "ltft_11771_001", "ltft_127718_001", "ltft_56636_001", "ltft_59701_001", "ltft_45276_001",
      "ltft_248393_001", "ltft_290986_001", "ltft_290986_002", "ltft_141956_001", "ltft_165766_001",
      "ltft_249312_001", "ltft_116333_001", "ltft_344716_001", "ltft_328307_001", "ltft_19070_001",
      "ltft_303242_001", "ltft_343557_001", "ltft_307330_001", "ltft_283581_002", "ltft_22438_001",
      "ltft_248173_001", "ltft_248639_001", "ltft_291744_001", "ltft_262995_001", "ltft_10948_001",
      "ltft_68948_001", "ltft_268417_001", "ltft_343931_001", "ltft_6409_001", "ltft_263910_001",
      "ltft_347440_001", "ltft_316035_001", "ltft_316035_002", "ltft_294247_001", "ltft_273482_001",
      "ltft_281460_001", "ltft_319047_003", "ltft_11910_001", "ltft_259459_001", "ltft_18993_001",
      "ltft_20575_001", "ltft_131987_001", "ltft_284111_001", "ltft_260870_001", "ltft_315217_001",
      "ltft_284726_001", "ltft_17393_001", "ltft_291392_001", "ltft_263976_001", "ltft_319047_002",
      "ltft_315467_001", "ltft_319047_001", "ltft_276769_001", "ltft_11910_003", "ltft_279906_001",
      "ltft_260165_001", "ltft_22724_001", "ltft_320472_002", "ltft_308227_001", "ltft_22771_002",
      "ltft_317134_001", "ltft_272468_001", "ltft_307301_001", "ltft_258978_001", "ltft_56068_001",
      "ltft_285736_001", "ltft_325669_001", "ltft_274402_001", "ltft_294247_002", "ltft_325628_001",
      "ltft_294899_001", "ltft_273482_002", "ltft_279906_002", "ltft_10518_001", "ltft_304957_001",
      "ltft_329434_001", "ltft_341389_002", "ltft_19070_002", "ltft_295080_001", "ltft_306692_001",
      "ltft_308199_001", "ltft_280955_001", "ltft_67161_002", "ltft_308107_001", "ltft_308312_001",
      "ltft_306660_001", "ltft_307475_001", "ltft_301167_001", "ltft_296619_001", "ltft_284935_001",
      "ltft_325508_001", "ltft_307571_001", "ltft_308088_001", "ltft_294906_001", "ltft_56757_001",
      "ltft_307353_001", "ltft_246924_001", "ltft_307564_002", "ltft_280839_001", "ltft_307601_001",
      "ltft_313691_001", "ltft_275689_001", "ltft_47165_20250201_001", "ltft_47165_20250101_002", "ltft_47165_20250101_003"
  );

  /**
   * Constructs the Mongock change unit.
   *
   * @param mongoTemplate The interface for operations on the DB
   * @param service       The LTFT service
   */
  public WithdrawLegacyLtft(MongoTemplate mongoTemplate, LtftService service) {
    this.mongoTemplate = mongoTemplate;
    this.service = service;
  }

  /**
   * Run a find and delete for the document's ID.
   */
  @Execution
  public void migrate() {
    List<LtftForm> forms = mongoTemplate.find(
        Query.query(Criteria.where("formRef").in(FORM_REFS)), LtftForm.class);

    Set<String> foundRefs = forms.stream()
        .map(LtftForm::getFormRef)
        .collect(Collectors.toSet());
    FORM_REFS.stream()
        .filter(ref -> !foundRefs.contains(ref))
        .forEach(ref -> log.warn("LTFT form [{}] not found.", ref));

    LtftFormDto.StatusDto.LftfStatusInfoDetailDto detail =
        LtftFormDto.StatusDto.LftfStatusInfoDetailDto.builder()
        .reason(REASON)
        .message(MESSAGE)
        .build();

    int withdrawnCount = 0;

    for (LtftForm form : forms) {
      LifecycleState state = form.getStatus().current().state();

      if (!LifecycleState.canTransitionTo(form, LifecycleState.WITHDRAWN)) {
        log.info("Skipping LTFT form [{}] (id [{}]), original state [{}] cannot transition to "
            + "WITHDRAWN.", form.getFormRef(), form.getId(), state);
        continue;
      }

      Optional<LtftFormDto> withdrawn = service.withdrawLtftForm(form.getId(), detail);

      if (withdrawn.isPresent()) {
        withdrawnCount++;
        log.info("Withdrawn LTFT form [{}] (id [{}]).", form.getFormRef(), form.getId());
      } else {
        log.warn("LTFT form [{}] (id [{}]) was not withdrawn, service returned empty. "
            + "Original state is [{}].", form.getFormRef(), form.getId(), state);
      }
    }

    log.info("Withdrew {} of {} listed LTFT forms.", withdrawnCount, FORM_REFS.size());
  }

  /**
   * Do not attempt rollback, any successfully deleted record should stay deleted.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'withdrawLegacyLtft' migration.");
  }
}
