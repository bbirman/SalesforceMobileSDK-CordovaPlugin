/*
 * Copyright (c) 2017-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.security;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.crypto.KeyGenerator;

/**
 * This class provides methods to generate a unique ID that can be used as an encryption
 * key. The key is derived from an AES-256 base using SecureRandom or AES-128 base using UUID.
 *
 * @author bhariharan
 */
public class SalesforceKeyGenerator {

    private static final String TAG = "SalesforceKeyGenerator";
    private static final String SHARED_PREF_FILE = "identifier.xml";
    private static final String ENCRYPTED_ID_SHARED_PREF_KEY = "encrypted_%s";
    private static final String ID_PREFIX = "id_";
    private static final String ADDENDUM = "addendum_%s";
    private static final String KEYSTORE_ALIAS = "com.salesforce.androidsdk.security.KEYPAIR";
    private static final String SHA1 = "SHA-1";
    private static final String SHA256 = "SHA-256";
    private static final String SHA1PRNG = "SHA1PRNG";
    private static final String AES = "AES";

    /**
     * Returns the unique ID being used. The default key length is 256 bits.
     *
     * @param name Unique name associated with this unique ID.
     * @return Unique ID.
     */
    public static String getUniqueId(String name) {
        return getUniqueId(name, 256);
    }

    /**
     * Returns the unique ID being used based on the key length.
     *
     * @param name Unique name associated with this unique ID.
     * @param length Key length.
     * @return Unique ID.
     */
    public static String getUniqueId(String name, int length) {
        return generateUniqueId(name, length);
    }

    /**
     * Returns the encryption key being used.
     *
     * @param name Unique name associated with this encryption key.
     * @return Encryption key.
     */
    public static String getEncryptionKey(String name) {
        return generateEncryptionKey(name);
    }

    /**
     * Returns a randomly generated 128-byte key that's URL safe.
     *
     * @return Random 128-byte key.
     */
    public static String getRandom128ByteKey() {
        final SecureRandom secureRandom = new SecureRandom();
        byte[] random = new byte[128];
        secureRandom.nextBytes(random);
        return Base64.encodeToString(random,Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
    }

    /**
     * Returns the SHA-256 hashed value of the supplied private key.
     *
     * @param privateKey Private key.
     * @return SHA-256 hash.
     */
    public static String getSHA256Hash(String privateKey) {
        String hashedString = null;
        byte[] privateKeyBytes = privateKey.getBytes(StandardCharsets.US_ASCII);
        try {
            final MessageDigest digest = MessageDigest.getInstance(SHA256);
            byte[] hash = digest.digest(privateKeyBytes);
            hashedString = Base64.encodeToString(hash, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch(Exception e) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while generating SHA-256 hash", e);
        }
        return hashedString;
    }

    /**
     * Generates an RSA keypair and returns the public key.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @param length Key length.
     * @return RSA public key.
     * @deprecated Will be removed in Mobile SDK 8.0. Use {@link KeyStoreWrapper#getRSAPublicKey(String, int)} instead.
     */
    public static PublicKey getRSAPublicKey(String name, int length) {
        return KeyStoreWrapper.getInstance().getRSAPublicKey(name, length);
    }

    /**
     * Generates an RSA keypair and returns the encoded public key string.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @param length Key length.
     * @return RSA public key string.
     * @deprecated Will be removed in Mobile SDK 8.0. Use {@link KeyStoreWrapper#getRSAPublicString(String, int)} instead.
     */
    public static String getRSAPublicString(String name, int length) {
        return KeyStoreWrapper.getInstance().getRSAPublicString(name, length);
    }

    /**
     * Generates an RSA keypair and returns the private key.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @param length Key length.
     * @return RSA private key.
     * @deprecated Will be removed in Mobile SDK 8.0. Use {@link KeyStoreWrapper#getRSAPrivateKey(String, int)} instead.
     */
    public static PrivateKey getRSAPrivateKey(String name, int length) {
        return KeyStoreWrapper.getInstance().getRSAPrivateKey(name, length);
    }

    /**
     * Upgrades the keys stored in SharedPrefs to encrypted keys. This is a one-time
     * migration step that's run while upgrading to Mobile SDK 7.1.
     */
    public synchronized static void upgradeTo7Dot1() {
        final SharedPreferences prefs = SalesforceSDKManager.getInstance().getAppContext().getSharedPreferences(SHARED_PREF_FILE, 0);
        final Map<String, ?> prefContents = prefs.getAll();
        if (prefContents != null) {
            final Set<String> keys = prefContents.keySet();
            for (final String key : keys) {
                if (key != null && key.startsWith("id")) {
                    final String value = prefs.getString(key, null);
                    if (value != null) {
                        final PublicKey publicKey = KeyStoreWrapper.getInstance().getRSAPublicKey(KEYSTORE_ALIAS);
                        final String keyBase = key.replaceFirst(ID_PREFIX, "");
                        final String mutatedValue = value + String.format(Locale.US, ADDENDUM, keyBase);
                        final String encryptedValue = Encryptor.encryptWithRSA(publicKey, mutatedValue);
                        storeInSharedPrefs(key, encryptedValue);
                        prefs.edit().remove(key).commit();
                    }
                }
            }
        }
    }

    private synchronized static String generateEncryptionKey(String name) {
        String encryptionKey = null;
        try {
            final String keyString = getUniqueId(name);
            byte[] secretKey = keyString.getBytes(StandardCharsets.UTF_8);
            final MessageDigest md = MessageDigest.getInstance(SHA1);
            secretKey = md.digest(secretKey);
            byte[] dest = new byte[16];
            System.arraycopy(secretKey, 0, dest, 0, 16);
            encryptionKey = Base64.encodeToString(dest, Base64.NO_WRAP);
        } catch (Exception ex) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while getting encryption key", ex);
        }
        return encryptionKey;
    }

    private synchronized static String generateUniqueId(String name, int length) {
        final String id = readFromSharedPrefs(ID_PREFIX + name);

        // Checks if we have a unique identifier stored.
        if (id != null) {
            final PrivateKey privateKey = KeyStoreWrapper.getInstance().getRSAPrivateKey(KEYSTORE_ALIAS);
            return Encryptor.decryptWithRSA(privateKey, id);
        } else {
            String uniqueId;
            try {

                // Uses SecureRandom to generate an AES-256 key.
                final SecureRandom secureRandom = SecureRandom.getInstance(SHA1PRNG);

                // SecureRandom does not require seeding. It's automatically seeded from system entropy.
                final KeyGenerator keyGenerator = KeyGenerator.getInstance(AES);
                keyGenerator.init(length, secureRandom);

                // Generates a 256-bit key.
                uniqueId = Base64.encodeToString(keyGenerator.generateKey().getEncoded(), Base64.NO_WRAP);
            } catch (NoSuchAlgorithmException e) {
                SalesforceSDKLogger.e(TAG, "Security exception thrown", e);

                // Generates a random UUID 128-bit key instead.
                uniqueId = UUID.randomUUID().toString();
            }
            final PublicKey publicKey = KeyStoreWrapper.getInstance().getRSAPublicKey(KEYSTORE_ALIAS);
            final String encryptedKey = Encryptor.encryptWithRSA(publicKey, uniqueId);
            storeInSharedPrefs(ID_PREFIX + name, encryptedKey);
            return uniqueId;
        }
    }

    private static String readFromSharedPrefs(String key) {
        final SharedPreferences prefs = SalesforceSDKManager.getInstance().getAppContext().getSharedPreferences(SHARED_PREF_FILE, 0);
        return prefs.getString(getSharedPrefKey(key), null);
    }

    private synchronized static void storeInSharedPrefs(String key, String value) {
        final SharedPreferences prefs = SalesforceSDKManager.getInstance().getAppContext().getSharedPreferences(SHARED_PREF_FILE, 0);
        prefs.edit().putString(getSharedPrefKey(key), value).commit();
    }

    private static String getSharedPrefKey(String name) {
        final String suffix = TextUtils.isEmpty(name) ? "" : name;
        return String.format(Locale.US, ENCRYPTED_ID_SHARED_PREF_KEY, suffix);
    }
}
