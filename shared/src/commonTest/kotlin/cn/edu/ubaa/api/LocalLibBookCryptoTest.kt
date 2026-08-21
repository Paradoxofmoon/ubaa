package cn.edu.ubaa.api

import cn.edu.ubaa.api.local.LocalLibBookCrypto
import cn.edu.ubaa.model.dto.LibBookReserveRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalLibBookCryptoTest {
  @Test
  fun `encrypt reserve request matches Python golden value`() {
    val encrypted =
        LocalLibBookCrypto.encryptReserveRequest(
            LibBookReserveRequest(
                areaId = "8",
                seatId = "101",
                day = "2026-05-08",
                segment = "seg-1",
            )
        )

    assertEquals(
        "lGWxL9YCYE0sXIQzPsUCs3jfaFPunT/NyR93uF2nVP1OQPYYihpMRBvm7jxYdUZNTMCyIRtdY8d3DgCNz8G3lmeWmPjvy6jV2KeuJXR8nrOmk26JK+ATZB1VXBNOFebA",
        encrypted,
    )
  }
}
