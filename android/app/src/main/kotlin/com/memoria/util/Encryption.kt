package com.memoria.util

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

object Encryption {
    
    private const val KEY_ALIAS = "MemoriaEncryptionKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            generateKey()
        }
    }
    
    @Throws(Exception::class)
    fun encryptEvent(event: com.memoria.model.EmergencyEvent): Map<String, Any?> {
        return mapOf(
            "id" to event.id,
            "timestamp" to event.timestamp,
            "type" to event.type,
            "confidence" to event.confidence,
            "location" to mapOf(
                "latitude" to event.location.latitude,
                "longitude" to event.location.longitude,
                "accuracy" to event.location.accuracy
            ),
            "audioPath" to event.audioPath,
            "photoPath" to event.photoPath,
            "telemetry" to event.telemetry,
            "deviceId" to event.deviceId
        )
    }
    
    @Throws(Exception::class)
    fun encryptFile(file: File): File {
        val input = file.readBytes()
        val (encrypted, iv) = encrypt(input)
        
        val encryptedFile = File.createTempFile("enc_", file.name, file.parentFile)
        encryptedFile.outputStream().use { out ->
            out.write(iv)
            out.write(encrypted)
        }
        
        return encryptedFile
    }
    
    @Throws(Exception::class)
    fun decryptFile(encryptedFile: File): File {
        val data = encryptedFile.readBytes()
        val iv = data.copyOfRange(0, 12)
        val encrypted = data.copyOfRange(12, data.size)
        
        val decrypted = decrypt(encrypted, iv)
        
        val decryptedFile = File.createTempFile("dec_", encryptedFile.name, encryptedFile.parentFile)
        decryptedFile.outputStream().use { out ->
            out.write(decrypted)
        }
        
        return decryptedFile
    }
    
    @Throws(Exception::class)
    private fun encrypt(data: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        
        return Pair(encrypted, iv)
    }
    
    @Throws(Exception::class)
    private fun decrypt(data: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), ivSpec)
        
        return cipher.doFinal(data)
    }
    
    @Throws(Exception::class)
    private fun generateKey() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
            
            val keySpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            
            keyGenerator.init(keySpec)
            keyGenerator.generateKey()
        }
    }
    
    @Throws(Exception::class)
    private fun getKey(): SecretKey {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val keystore = KeyStore.getInstance("AndroidKeyStore")
            keystore.load(null)
            val entry = keystore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            entry.secretKey
        } else {
            throw Exception("Encryption not supported on this Android version")
        }
    }
}
