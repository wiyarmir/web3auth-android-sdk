package com.web3auth.core.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.web3auth.core.Web3AuthApp
import org.web3j.crypto.ECKeyPair
import java.math.BigInteger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import kotlin.text.Charsets.UTF_8


object KeyStoreManagerUtils {

    private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"
    private const val Android_KEY_STORE = "AndroidKeyStore"
    private const val WEB3AUTH = "Web3Auth"
    const val SESSION_ID = "sessionId"
    const val IV_KEY = "ivKey"
    const val EPHEM_PUBLIC_Key = "ephemPublicKey"
    private lateinit var encryptedPairData: Pair<ByteArray, ByteArray>

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val sharedPreferences = Web3AuthApp.getContext()?.let {
        EncryptedSharedPreferences.create(
        "Web3Auth",
        masterKeyAlias,
            it,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    }

    /*
    * Key generator to encrypt and decrypt data
    * */
    @RequiresApi(Build.VERSION_CODES.M)
    fun getKeyGenerator() {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, Android_KEY_STORE)
        val keyGeneratorSpec = KeyGenParameterSpec.Builder(
            WEB3AUTH,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(keyGeneratorSpec)
        keyGenerator.generateKey()
    }

    fun encryptData(key: String, data: String) {
        sharedPreferences?.edit()?.putString(key, data)?.apply()
        encryptedPairData = getEncryptedDataPair(data)
        encryptedPairData.second.toString(UTF_8)
    }

    private fun getEncryptedDataPair(data: String): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())

        val iv: ByteArray = cipher.iv
        val encryptedData = cipher.doFinal(data.toByteArray(UTF_8))
        return Pair(iv, encryptedData)
    }

    fun decryptData(key: String): String {
        val encryptedPairData = sharedPreferences?.getString(key, "")?.let { getEncryptedDataPair(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val keySpec = IvParameterSpec(encryptedPairData?.first)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), keySpec)
        return cipher.doFinal(encryptedPairData?.second).toString(UTF_8)
    }

    /*
    * Store encrypted data into preferences
    * */
    fun savePreferenceData(key: String, data: String) {
        sharedPreferences?.edit()?.putString(key, data)?.apply()
    }

    /*
    * Retrieve decrypted data from preferences
    * */
    fun getPreferencesData(key: String): String? {
        return sharedPreferences?.getString(key, "")
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(Android_KEY_STORE)
        keyStore.load(null)
        val secreteKeyEntry: KeyStore.SecretKeyEntry = keyStore.getEntry(WEB3AUTH, null) as KeyStore.SecretKeyEntry
        return secreteKeyEntry.secretKey
    }

    /*
    * get Public key from sessionID
    * */
    fun getPubKey(sessionId: String): String {
        val derivedECKeyPair: ECKeyPair = ECKeyPair.create(BigInteger(sessionId, 16))
        return derivedECKeyPair.publicKey.toString(16)
    }

    /*
    * get Private key from sessionID
    * */
    fun getPrivateKey(sessionId: String): String {
        val derivedECKeyPair: ECKeyPair = ECKeyPair.create(BigInteger(sessionId, 16))
        return derivedECKeyPair.privateKey.toString(16)
    }

    fun padLeft(inputString: String, padChar: Char?, length: Int): String {
        if (inputString.length >= length) return inputString
        val sb = StringBuilder()
        while (sb.length < length - inputString.length) {
            sb.append(padChar)
        }
        sb.append(inputString)
        return sb.toString()
    }
}