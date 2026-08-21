package cn.edu.ubaa.libbook

import cn.edu.ubaa.auth.JwtAuth.requireUserSession
import cn.edu.ubaa.auth.respondError
import cn.edu.ubaa.metrics.BusinessOperationScope
import cn.edu.ubaa.metrics.observeBusinessOperation
import cn.edu.ubaa.model.dto.LibBookReserveRequest
import cn.edu.ubaa.utils.UpstreamTimeoutException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.libBookRouting() {
  val libBookService = GlobalLibBookService.instance

  route("/api/v1/libbook") {
    get("/libraries") {
      val session = call.requireUserSession()
      val day =
          call.request.queryParameters["day"]
              ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_request")
      call.observeBusinessOperation("libbook", "list_libraries") {
        call.runLibBookCall(this) {
          call.respond(HttpStatusCode.OK, libBookService.getLibraries(session.username, day))
        }
      }
    }

    get("/areas") {
      val session = call.requireUserSession()
      val premisesId =
          call.request.queryParameters["premisesId"]
              ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_request")
      val storeyId = call.request.queryParameters["storeyId"]
      val day =
          call.request.queryParameters["day"]
              ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_request")
      call.observeBusinessOperation("libbook", "list_areas") {
        call.runLibBookCall(this) {
          call.respond(
              HttpStatusCode.OK,
              libBookService.getAreas(session.username, premisesId, storeyId, day),
          )
        }
      }
    }

    get("/areas/{areaId}") {
      val session = call.requireUserSession()
      val areaId =
          call.parameters["areaId"]?.takeIf { it.isNotBlank() }
              ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_request")
      call.observeBusinessOperation("libbook", "get_area_info") {
        call.runLibBookCall(this) {
          call.respond(HttpStatusCode.OK, libBookService.getAreaDetail(session.username, areaId))
        }
      }
    }

    get("/areas/{areaId}/seats") {
      val session = call.requireUserSession()
      val areaId =
          call.parameters["areaId"]?.takeIf { it.isNotBlank() }
              ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_request")
      val day =
          call.request.queryParameters["day"]
              ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_request")
      val startTime = call.request.queryParameters["startTime"].orEmpty()
      val endTime = call.request.queryParameters["endTime"].orEmpty()
      call.observeBusinessOperation("libbook", "list_seats") {
        call.runLibBookCall(this) {
          call.respond(
              HttpStatusCode.OK,
              libBookService.getSeats(session.username, areaId, day, startTime, endTime),
          )
        }
      }
    }

    post("/bookings") {
      val session = call.requireUserSession()
      val request =
          try {
            call.receive<LibBookReserveRequest>()
          } catch (_: Exception) {
            return@post call.respondError(HttpStatusCode.BadRequest, "invalid_request")
          }
      call.observeBusinessOperation("libbook", "reserve") {
        call.runLibBookCall(this) {
          call.respond(HttpStatusCode.OK, libBookService.reserve(session.username, request))
        }
      }
    }

    get("/reservations") {
      val session = call.requireUserSession()
      val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
      val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
      call.observeBusinessOperation("libbook", "list_bookings") {
        call.runLibBookCall(this) {
          call.respond(HttpStatusCode.OK, libBookService.getBookings(session.username, page, limit))
        }
      }
    }

    post("/bookings/{bookingId}/cancel") {
      val session = call.requireUserSession()
      val bookingId =
          call.parameters["bookingId"]?.takeIf { it.isNotBlank() }
              ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_request")
      call.observeBusinessOperation("libbook", "cancel_booking") {
        call.runLibBookCall(this) {
          call.respond(HttpStatusCode.OK, libBookService.cancelBooking(session.username, bookingId))
        }
      }
    }
  }
}

private suspend fun ApplicationCall.runLibBookCall(
    scope: BusinessOperationScope,
    block: suspend () -> Unit,
) {
  try {
    block()
  } catch (e: UpstreamTimeoutException) {
    scope.markTimeout()
    respondError(HttpStatusCode.GatewayTimeout, e.code)
  } catch (e: LibBookAuthenticationException) {
    scope.markUnauthenticated()
    respondError(HttpStatusCode.BadGateway, "libbook_auth_failed")
  } catch (e: LibBookException) {
    val status =
        when (e.code) {
          "invalid_request" -> HttpStatusCode.BadRequest
          "libbook_not_found" -> HttpStatusCode.NotFound
          "libbook_seat_unavailable" -> HttpStatusCode.Conflict
          else -> HttpStatusCode.BadGateway
        }
    if (status == HttpStatusCode.BadRequest || status == HttpStatusCode.Conflict) {
      scope.markBusinessFailure()
    } else {
      scope.markError()
    }
    respondError(status, e.code, e.message)
  } catch (e: Exception) {
    scope.markError()
    respondError(HttpStatusCode.InternalServerError, "internal_server_error")
  }
}
