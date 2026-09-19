package com.ticketkeep.app.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 客户端备份加解密：PBKDF2-HmacSHA256 派生密钥 + AES-GCM。
 * 口令不离开本机；密文格式可单测。服务器只存密文字节。
 */
object BackupCrypto {
    const val MAGIC = "TKBK1"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_LEN_BITS = 256
    private const val ITERATIONS = 120_000
    private const val GCM_TAG_BITS = 128

    sealed class Outcome {
        data class Ok(val bytes: ByteArray) : Outcome()
        data class Error(val message: String) : Outcome()
    }

    /**
     * 用备份口令加密明文；输出 = magic + salt + iv + ciphertext+tag。
     */
    fun encrypt(plain: ByteArray, passphrase: CharArray): Outcome {
        if (passphrase.isEmpty()) return Outcome.Error("请设置备份口令")
        return try {
            val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val ct = cipher.doFinal(plain)
            val magic = MAGIC.toByteArray(Charsets.US_ASCII)
            val out = ByteArray(magic.size + salt.size + iv.size + ct.size)
            var o = 0
            System.arraycopy(magic, 0, out, o, magic.size); o += magic.size
            System.arraycopy(salt, 0, out, o, salt.size); o += salt.size
            System.arraycopy(iv, 0, out, o, iv.size); o += iv.size
            System.arraycopy(ct, 0, out, o, ct.size)
            Outcome.Ok(out)
        } catch (e: Exception) {
            Outcome.Error("加密失败：${e.message ?: "未知错误"}")
        }
    }

    /**
     * 解密密文；口令错误或格式损坏时返回中文错误，不抛未捕获异常。
     */
    fun decrypt(blob: ByteArray, passphrase: CharArray): Outcome {
        if (passphrase.isEmpty()) return Outcome.Error("请输入备份口令")
        val magic = MAGIC.toByteArray(Charsets.US_ASCII)
        if (blob.size < magic.size + SALT_LEN + IV_LEN + 16) {
            return Outcome.Error("备份文件损坏或格式不正确")
        }
        for (i in magic.indices) {
            if (blob[i] != magic[i]) return Outcome.Error("不是本应用的加密备份包")
        }
        return try {
            var o = magic.size
            val salt = blob.copyOfRange(o, o + SALT_LEN); o += SALT_LEN
            val iv = blob.copyOfRange(o, o + IV_LEN); o += IV_LEN
            val ct = blob.copyOfRange(o, blob.size)
            val key = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            Outcome.Ok(cipher.doFinal(ct))
        } catch (_: Exception) {
            Outcome.Error("解密失败：口令错误或文件已损坏")
        }
    }

    fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_LEN_BITS)
        val encoded = factory.generateSecret(spec).encoded
        return SecretKeySpec(encoded, "AES")
    }
}
