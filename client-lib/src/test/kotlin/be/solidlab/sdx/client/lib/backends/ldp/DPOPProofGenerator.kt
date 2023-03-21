package be.solidlab.sdx.client.lib.backends.ldp

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.io.IOException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.InvalidKeySpecException
import java.text.ParseException
import java.util.*

fun main(args: Array<String>) {
    val keyPairType = "EC"
    //String keyPairType = "RSA";
    var gen = KeyPairGenerator.getInstance(keyPairType)
    val keyPair: KeyPair
    var jwk: JWK? = null
    if (keyPairType == "EC") {
        gen.initialize(Curve.P_256.toECParameterSpec())
        keyPair = gen.generateKeyPair()
        jwk = ECKey.Builder(Curve.P_256, keyPair.public as ECPublicKey)
            .build()
    } else {
        gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        keyPair = gen.generateKeyPair()
        jwk = RSAKey.Builder(keyPair.public as RSAPublicKey).build()
    }
    println("jwk: $jwk")
    println("publicKey: " + keyPair.public)
    println("private: " + keyPair.private)
    val jwtClaimsSetBuilder = JWTClaimsSet.Builder()
    jwtClaimsSetBuilder.issuer("issuer")
    jwtClaimsSetBuilder.subject("sub")
    jwtClaimsSetBuilder.issueTime(Date(System.currentTimeMillis()))
    jwtClaimsSetBuilder.jwtID(UUID.randomUUID().toString())
    jwtClaimsSetBuilder.notBeforeTime(Date(System.currentTimeMillis()))
    jwtClaimsSetBuilder.claim("htm", "POST")
    jwtClaimsSetBuilder.claim("htu", "http://localhost:3000/.oidc/token")
    val headerBuilder: JWSHeader.Builder
    headerBuilder = if (keyPairType == "EC") {
        JWSHeader.Builder(JWSAlgorithm.ES256)
    } else {
        JWSHeader.Builder(JWSAlgorithm.RS384)
    }
    headerBuilder.type(JOSEObjectType("dpop+jwt"))
    headerBuilder.jwk(jwk)
    val signedJWT = SignedJWT(headerBuilder.build(), jwtClaimsSetBuilder.build())
    if (keyPairType == "EC") {
        val ecdsaSigner = ECDSASigner(keyPair.private, Curve.P_256)
        signedJWT.sign(ecdsaSigner)
    } else {
        val rsassaSigner = RSASSASigner(keyPair.private)
        signedJWT.sign(rsassaSigner)
    }
    println("[Signed JWT] : " + signedJWT.serialize())
    val parseJwk = JWK.parse(jwk.toString())
    if (keyPairType == "EC") {
        val ecKey = parseJwk as ECKey
        println("[ThumbPrint] : " + ecKey.computeThumbprint().toString())
    } else {
        val rsaKey = parseJwk as RSAKey
        println("[ThumbPrint] : " + rsaKey.computeThumbprint().toString())
    }
}
