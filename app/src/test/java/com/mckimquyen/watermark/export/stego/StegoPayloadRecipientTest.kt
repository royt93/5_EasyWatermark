package com.mckimquyen.watermark.export.stego

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * IDEA-10: dấu vân tay theo người nhận chỉ có giá trị nếu 2 người nhận KHÁC NHAU cho ra ID khác
 * nhau, và cùng một người nhận luôn cho ra ID giống nhau (nếu không thì tra ngược vô nghĩa).
 */
class StegoPayloadRecipientTest {

    private val owner = "Roy Studio"

    private fun fingerprint(code: String) = StegoPayload.ownerIdOf("$owner#$code")

    @Test
    fun `hai nguoi nhan khac nhau cho ID khac nhau`() {
        assertThat(fingerprint("VIP-A")).isNotEqualTo(fingerprint("VIP-B"))
    }

    @Test
    fun `cung mot nguoi nhan luon cho ID on dinh qua nhieu lan goi`() {
        val first = fingerprint("VIP-A")
        val second = fingerprint("VIP-A")
        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `co gan nguoi nhan thi khac hoan toan voi khong gan`() {
        // Đây là tính chất khiến màn verify phân biệt được "ảnh của tôi" và "ảnh đã gửi cho khách".
        assertThat(fingerprint("VIP-A")).isNotEqualTo(StegoPayload.ownerIdOf(owner))
    }

    @Test
    fun `chu so huu khac nhau cung ma nguoi nhan van cho ID khac nhau`() {
        val a = StegoPayload.ownerIdOf("Studio A#VIP-A")
        val b = StegoPayload.ownerIdOf("Studio B#VIP-A")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `payload nguoi nhan di duoc tron vong encode decode`() {
        val id = fingerprint("VIP-A")
        val decoded = StegoPayload.decode(StegoPayload.encode(id))
        assertThat(decoded).isNotNull()
        assertThat(decoded!!.ownerId).isEqualTo(id)
    }

    @Test
    fun `ma nguoi nhan chi khac nhau 1 ky tu cung cho ID khac nhau`() {
        // FNV-1a phân bố đều; nếu 2 mã gần giống nhau mà trùng ID thì tra nguồn rò rỉ sẽ chỉ sai người.
        assertThat(fingerprint("VIP-A1")).isNotEqualTo(fingerprint("VIP-A2"))
    }

    /**
     * 200 mã người nhận sinh máy móc không được đụng độ nhau — kiểm chứng phân bố hash đủ tốt cho
     * quy mô dùng thật (studio hiếm khi quản lý quá vài trăm khách).
     */
    @Test
    fun `hai tram ma nguoi nhan khong trung ID nao`() {
        val ids = (1..200).map { fingerprint("CLIENT-$it") }.toSet()
        assertThat(ids).hasSize(200)
    }
}
