package com.inputleaf.android.network

class ClientCertificateMaterial(
    val pkcs12: ByteArray,
    val password: CharArray,
) {
    fun clear() {
        pkcs12.fill(0)
        password.fill('\u0000')
    }
}
