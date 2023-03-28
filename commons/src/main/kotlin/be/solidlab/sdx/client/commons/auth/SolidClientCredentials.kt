package be.solidlab.sdx.client.commons.auth

data class SolidClientCredentials(
    val identityServerUrl: String,
    val clientId: String,
    val clientSecret: String
)
