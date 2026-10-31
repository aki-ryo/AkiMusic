package com.akiryo.akimusic.data

import java.math.BigInteger
import java.security.MessageDigest
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云 weapi 加密（AES-128-CBC 两遍 + RSA 无填充）。
 * 密钥/IV/模数来自公开的开源实现（NeteaseCloudMusicApi），仅用于登录请求。
 */
object WeapiCrypto {

    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val PUB_KEY =
        "010001"
    private const val MODULUS =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"
    private const val EAPI_PUB =
        "010001"
    private const val EAPI_MODULUS =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"

    private fun aesEncrypt(text: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(text)
    }

    private fun rsaEncrypt(text: ByteArray, pubKey: String, modulus: String): String {
        val reversed = text.reversedArray()
        val hex = BigInteger(1, reversed).toString(16)
        val padded = hex.padStart(256, '0')
        val m = BigInteger(modulus, 16)
        val e = BigInteger(pubKey, 16)
        val result = BigInteger(padded, 16).modPow(e, m).toString(16)
        return result.padStart(256, '0')
    }

    private fun randomKey(): String {
        val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val rnd = Random()
        return buildString(16) { repeat(16) { append(chars[rnd.nextInt(chars.length)]) } }
    }

    /** weapi 加密：返回 params + encSecKey */
    fun encrypt(text: String): Pair<String, String> {
        val secretKey = randomKey()
        val keyBytes = secretKey.toByteArray(Charsets.UTF_8)
        val ivBytes = IV.toByteArray(Charsets.UTF_8)
        val textBytes = text.toByteArray(Charsets.UTF_8)

        val first = aesEncrypt(textBytes, PRESET_KEY.toByteArray(Charsets.UTF_8), ivBytes)
        val params = aesEncrypt(first, keyBytes, ivBytes)
        val encSecKey = rsaEncrypt(keyBytes, PUB_KEY, MODULUS)

        return params.toBase64() to encSecKey
    }

    private fun ByteArray.toBase64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
}
