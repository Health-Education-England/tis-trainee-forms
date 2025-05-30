package uk.nhs.hee.tis.trainee.forms.dto;

import lombok.Builder;
import org.bson.types.ObjectId;

import java.util.UUID;

@Builder
public record NotificationEventDto(
    UUID formId,
    String type,   //LTFT_SUBMITTED_TPD
    String status //failed, sent, pending
) {

}
