package yeo.chi.proejct.pms.reservation.controller

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.domain.RequestResultStatus
import yeo.chi.proejct.pms.reservation.domain.ReservationRequest
import yeo.chi.proejct.pms.reservation.domain.ReservationRequestAction
import java.time.OffsetDateTime

class ReservationRequestHttpStatusTest : FeatureSpec({
    fun reservationRequest(
        resultStatus: RequestResultStatus,
        rejectReason: String?,
    ): ReservationRequest =
        ReservationRequest(
            id = 1L,
            requestKey = "REQUEST-KEY",
            reservationId = 1L,
            platformId = "OTA_BOOKING",
            action = ReservationRequestAction.BOOK,
            initiatedBy = RequestInitiator.OTA,
            roomCode = "ROOM-101",
            oldDateRange = null,
            newDateRange = null,
            resultStatus = resultStatus,
            rejectReason = rejectReason,
            requestedAt = OffsetDateTime.now(),
        )

    feature("ReservationRequest.toHttpStatus") {
        scenario("SUCCESS는 200으로 매핑된다") {
            reservationRequest(RequestResultStatus.SUCCESS, null).toHttpStatus() shouldBe HttpStatus.OK
        }

        scenario("CONFLICT(DUPLICATE_BOOKING)는 409로 매핑된다") {
            reservationRequest(RequestResultStatus.CONFLICT, "DUPLICATE_BOOKING").toHttpStatus() shouldBe HttpStatus.CONFLICT
        }

        scenario("FAILED + RESERVATION_NOT_FOUND는 404로 매핑된다") {
            reservationRequest(RequestResultStatus.FAILED, "RESERVATION_NOT_FOUND").toHttpStatus() shouldBe HttpStatus.NOT_FOUND
        }

        scenario("FAILED + ALREADY_CANCELLED는 409로 매핑된다") {
            reservationRequest(RequestResultStatus.FAILED, "ALREADY_CANCELLED").toHttpStatus() shouldBe HttpStatus.CONFLICT
        }

        scenario("FAILED + RESERVATION_NOT_CHANGEABLE는 409로 매핑된다") {
            reservationRequest(RequestResultStatus.FAILED, "RESERVATION_NOT_CHANGEABLE").toHttpStatus() shouldBe HttpStatus.CONFLICT
        }

        scenario("FAILED + 알려지지 않은 rejectReason은 안전한 기본값인 409로 매핑된다") {
            reservationRequest(RequestResultStatus.FAILED, "SOMETHING_NEW").toHttpStatus() shouldBe HttpStatus.CONFLICT
        }
    }
})
