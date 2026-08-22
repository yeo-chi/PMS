package yeo.chi.proejct.pms.reservation.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import yeo.chi.proejct.pms.reservation.controller.data.BookReservationRequest
import yeo.chi.proejct.pms.reservation.controller.data.CancelConfirmRequest
import yeo.chi.proejct.pms.reservation.controller.data.CancelRequestRequestBody
import yeo.chi.proejct.pms.reservation.controller.data.ChangeReservationRequest
import yeo.chi.proejct.pms.reservation.domain.CancelRequestReason
import yeo.chi.proejct.pms.reservation.domain.RequestInitiator
import yeo.chi.proejct.pms.reservation.persistent.PostgresIntegrationTest
import yeo.chi.proejct.pms.reservation.persistent.repository.ReservationRepository
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
class ReservationControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val reservationRepository: ReservationRepository,
) : PostgresIntegrationTest({

    fun book(
        platformReservationRef: String,
        roomCode: String,
        startDate: LocalDate,
        endDate: LocalDate,
        initiatedBy: RequestInitiator = RequestInitiator.OTA,
    ): ResultActions =
        mockMvc.perform(
            post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        BookReservationRequest(
                            platformId = "OTA_BOOKING",
                            platformReservationRef = platformReservationRef,
                            roomId = roomCode,
                            startDate = startDate,
                            endDate = endDate,
                            initiatedBy = initiatedBy,
                        ),
                    ),
                ),
        )

    // cancel-request 엔드포인트는 URL 경로에 내부 PK(Long)를 쓰지만, BOOK 응답 바디는 논리 키인
    // reservationCode(String)만 돌려준다 — 응답으로 받은 reservationCode로 다시 조회해 내부 PK를 얻는다.
    fun bookedReservationId(
        platformReservationRef: String,
        roomCode: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Long {
        val response = book(platformReservationRef, roomCode, startDate, endDate).andReturn().response
        val reservationCode = objectMapper.readTree(response.contentAsString).get("reservationCode").asText()
        return checkNotNull(reservationRepository.findByReservationCode(reservationCode)).id
    }

    fun cancelRequest(
        reservationId: Long,
        reason: CancelRequestReason = CancelRequestReason.OVERBOOKING_CLEANUP,
    ): ResultActions =
        mockMvc.perform(
            post("/api/reservations/$reservationId/cancel-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CancelRequestRequestBody(reason))),
        )

    fun cancelConfirm(
        platformReservationRef: String,
        externalRequestId: String? = null,
    ): ResultActions =
        mockMvc.perform(
            post("/api/reservations/cancel-confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CancelConfirmRequest("OTA_BOOKING", platformReservationRef, externalRequestId),
                    ),
                ),
        )

    fun change(
        platformReservationRef: String,
        newStartDate: LocalDate,
        newEndDate: LocalDate,
        initiatedBy: RequestInitiator = RequestInitiator.OTA,
        externalRequestId: String? = null,
    ): ResultActions =
        mockMvc.perform(
            post("/api/reservations/change")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        ChangeReservationRequest(
                            platformId = "OTA_BOOKING",
                            platformReservationRef = platformReservationRef,
                            newStartDate = newStartDate,
                            newEndDate = newEndDate,
                            initiatedBy = initiatedBy,
                            externalRequestId = externalRequestId,
                        ),
                    ),
                ),
        )

    feature("BOOK") {
        scenario("성공하면 201과 함께 SUCCESS 결과를 반환한다") {
            book("REF-CTRL-BOOK-1", "ROOM-CTRL-BOOK-1", LocalDate.of(2031, 1, 1), LocalDate.of(2031, 1, 5))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.resultStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.reservationCode").isString)
        }

        scenario("같은 room_code에 겹치는 날짜로 요청하면 409와 DUPLICATE_BOOKING을 반환한다") {
            book("REF-CTRL-BOOK-2-A", "ROOM-CTRL-BOOK-2", LocalDate.of(2031, 2, 1), LocalDate.of(2031, 2, 10))
                .andExpect(status().isCreated)

            book("REF-CTRL-BOOK-2-B", "ROOM-CTRL-BOOK-2", LocalDate.of(2031, 2, 5), LocalDate.of(2031, 2, 15))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.rejectReason").value("DUPLICATE_BOOKING"))
        }

        scenario("initiatedBy=HOST면 400을 반환한다") {
            book(
                "REF-CTRL-BOOK-HOST",
                "ROOM-CTRL-BOOK-HOST",
                LocalDate.of(2031, 3, 1),
                LocalDate.of(2031, 3, 5),
                initiatedBy = RequestInitiator.HOST,
            ).andExpect(status().isBadRequest)
        }

        scenario("startDate가 endDate보다 이전이 아니면 400을 반환한다") {
            book("REF-CTRL-BOOK-INVERTED", "ROOM-CTRL-BOOK-INVERTED", LocalDate.of(2031, 4, 5), LocalDate.of(2031, 4, 1))
                .andExpect(status().isBadRequest)
        }

        scenario("필수 필드가 비어있으면 400을 반환한다") {
            val invalidBody =
                """
                {
                  "platformId": "",
                  "platformReservationRef": "REF-CTRL-BOOK-BLANK",
                  "roomCode": "ROOM-CTRL-BOOK-BLANK",
                  "startDate": "2031-05-01",
                  "endDate": "2031-05-05",
                  "initiatedBy": "OTA"
                }
                """.trimIndent()

            mockMvc
                .perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(invalidBody))
                .andExpect(status().isBadRequest)
        }
    }

    feature("cancelRequest") {
        scenario("성공하면 200과 함께 SUCCESS 결과를 반환한다") {
            val reservationId =
                bookedReservationId("REF-CTRL-CANCEL-REQ-1", "ROOM-CTRL-CANCEL-REQ-1", LocalDate.of(2031, 6, 1), LocalDate.of(2031, 6, 5))

            cancelRequest(reservationId)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.resultStatus").value("SUCCESS"))
        }

        scenario("존재하지 않는 reservationId면 404를 반환한다") {
            cancelRequest(999_999_999L).andExpect(status().isNotFound)
        }

        scenario("이미 CANCELLED인 예약이면 409와 ALREADY_CANCELLED를 반환한다") {
            val reservationId =
                bookedReservationId("REF-CTRL-CANCEL-REQ-2", "ROOM-CTRL-CANCEL-REQ-2", LocalDate.of(2031, 7, 1), LocalDate.of(2031, 7, 5))
            cancelConfirm("REF-CTRL-CANCEL-REQ-2").andExpect(status().isOk)

            cancelRequest(reservationId)
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.rejectReason").value("ALREADY_CANCELLED"))
        }
    }

    feature("cancelConfirm") {
        scenario("성공하면 200을 반환한다") {
            bookedReservationId("REF-CTRL-CANCEL-CONFIRM-1", "ROOM-CTRL-CANCEL-CONFIRM-1", LocalDate.of(2031, 8, 1), LocalDate.of(2031, 8, 5))

            cancelConfirm("REF-CTRL-CANCEL-CONFIRM-1")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.resultStatus").value("SUCCESS"))
        }

        scenario("존재하지 않는 예약이면 404를 반환한다") {
            cancelConfirm("REF-CTRL-CANCEL-CONFIRM-NOT-FOUND").andExpect(status().isNotFound)
        }
    }

    feature("change") {
        scenario("성공하면 200을 반환한다") {
            bookedReservationId("REF-CTRL-CHANGE-1", "ROOM-CTRL-CHANGE-1", LocalDate.of(2031, 9, 1), LocalDate.of(2031, 9, 5))

            change("REF-CTRL-CHANGE-1", LocalDate.of(2031, 10, 1), LocalDate.of(2031, 10, 5))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.resultStatus").value("SUCCESS"))
        }

        scenario("겹치는 날짜로 변경하면 409와 DUPLICATE_BOOKING을 반환한다") {
            book("REF-CTRL-CHANGE-2-OTHER", "ROOM-CTRL-CHANGE-2", LocalDate.of(2031, 11, 10), LocalDate.of(2031, 11, 20))
                .andExpect(status().isCreated)
            bookedReservationId("REF-CTRL-CHANGE-2", "ROOM-CTRL-CHANGE-2", LocalDate.of(2031, 11, 1), LocalDate.of(2031, 11, 5))

            change("REF-CTRL-CHANGE-2", LocalDate.of(2031, 11, 12), LocalDate.of(2031, 11, 18))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.rejectReason").value("DUPLICATE_BOOKING"))
        }

        scenario("PENDING_CANCEL 상태의 예약을 변경하려 하면 409와 RESERVATION_NOT_CHANGEABLE을 반환한다") {
            val reservationId =
                bookedReservationId("REF-CTRL-CHANGE-3", "ROOM-CTRL-CHANGE-3", LocalDate.of(2031, 12, 1), LocalDate.of(2031, 12, 5))
            cancelRequest(reservationId).andExpect(status().isOk)

            change("REF-CTRL-CHANGE-3", LocalDate.of(2032, 1, 1), LocalDate.of(2032, 1, 5))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.rejectReason").value("RESERVATION_NOT_CHANGEABLE"))
        }

        scenario("initiatedBy=HOST면 400을 반환한다") {
            change(
                "REF-CTRL-CHANGE-HOST",
                LocalDate.of(2032, 2, 1),
                LocalDate.of(2032, 2, 5),
                initiatedBy = RequestInitiator.HOST,
            ).andExpect(status().isBadRequest)
        }
    }
})
