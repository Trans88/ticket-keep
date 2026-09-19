package com.ticketkeep.app.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份加解密关键路径：往返一致、错口令失败、坏魔数失败。
 */
class BackupCryptoTest {

    @Test
    fun encryptDecrypt_roundTrip() {
        val plain = "票证记-hello-密文测试".toByteArray(Charsets.UTF_8)
        val pass = "secret-passphrase".toCharArray()
        val enc = BackupCrypto.encrypt(plain, pass)
        assertTrue(enc is BackupCrypto.Outcome.Ok)
        val blob = (enc as BackupCrypto.Outcome.Ok).bytes
        assertTrue(blob.size > plain.size)
        val dec = BackupCrypto.decrypt(blob, pass)
        assertTrue(dec is BackupCrypto.Outcome.Ok)
        assertArrayEquals(plain, (dec as BackupCrypto.Outcome.Ok).bytes)
    }

    @Test
    fun wrongPassphrase_fails() {
        val plain = byteArrayOf(1, 2, 3, 4, 5)
        val enc = BackupCrypto.encrypt(plain, "right-pass".toCharArray()) as BackupCrypto.Outcome.Ok
        val dec = BackupCrypto.decrypt(enc.bytes, "wrong-pass".toCharArray())
        assertTrue(dec is BackupCrypto.Outcome.Error)
        assertTrue((dec as BackupCrypto.Outcome.Error).message.contains("解密"))
    }

    @Test
    fun badMagic_fails() {
        val junk = "NOTBK".toByteArray() + ByteArray(40)
        val dec = BackupCrypto.decrypt(junk, "any".toCharArray())
        assertTrue(dec is BackupCrypto.Outcome.Error)
    }
}
