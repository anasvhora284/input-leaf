package com.inputleaf.android.testutil

import com.inputleaf.android.network.ClientCertificateMaterial
import java.util.Base64

object ClientCertificateTestFixture {
    const val PASSWORD = "testpass"

    fun material(): ClientCertificateMaterial {
        val encoded = checkNotNull(
            ClientCertificateTestFixture::class.java.classLoader
                ?.getResourceAsStream("test_client.p12.b64")
        ).bufferedReader().use { it.readText() }
        return ClientCertificateMaterial(
            pkcs12 = Base64.getMimeDecoder().decode(encoded),
            password = PASSWORD.toCharArray(),
        )
    }
}
