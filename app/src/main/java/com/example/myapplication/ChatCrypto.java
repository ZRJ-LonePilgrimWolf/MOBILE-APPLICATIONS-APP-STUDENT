package com.example.myapplication;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * End-to-end encryption for private messages.
 *
 * Each account gets an RSA key pair ON THIS PHONE, inside the Android Keystore.
 *  - The PUBLIC key is uploaded to the server so others can lock messages for you.
 *  - The PRIVATE key can never be read out of the Keystore, so only this phone can unlock them.
 *
 * Sending: a fresh random AES-256 key encrypts the text (AES-GCM). That AES key is then locked twice
 * with RSA: once for the recipient (k_to) and once for the sender (k_from). The server only ever sees
 * these locked values.
 */
public final class ChatCrypto {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String RSA_MODE = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
    private static final SecureRandom RANDOM = new SecureRandom();

    private ChatCrypto() { }

    private static String alias(int accountId) {
        return "chat_e2e_" + accountId;
    }

    /** Creates this account's key pair on this phone if needed, and returns the PUBLIC key (Base64). */
    public static synchronized String ensureKeyPair(int accountId) throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        String alias = alias(accountId);
        if (!ks.containsAlias(alias)) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE);
            kpg.initialize(new KeyGenParameterSpec.Builder(alias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .build());
            kpg.generateKeyPair();
        }
        PublicKey pub = ks.getCertificate(alias).getPublicKey();
        return b64(pub.getEncoded());
    }

    /** Encrypts a private message for the recipient AND for yourself. */
    public static ChatDtos.EncryptedPayload encrypt(String text, String recipientPublicKey, String myPublicKey)
            throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey aes = kg.generateKey();

        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.ENCRYPT_MODE, aes, new GCMParameterSpec(128, iv));
        byte[] ct = gcm.doFinal(text.getBytes(StandardCharsets.UTF_8));

        ChatDtos.EncryptedPayload p = new ChatDtos.EncryptedPayload();
        p.iv = b64(iv);
        p.ct = b64(ct);
        p.k_to = b64(lockKey(aes, recipientPublicKey));
        p.k_from = b64(lockKey(aes, myPublicKey));
        return p;
    }

    /** Decrypts a private message with this phone's private key. */
    public static String decrypt(ChatDtos.EncryptedPayload p, int myAccountId, boolean iSentIt) throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        PrivateKey priv = (PrivateKey) ks.getKey(alias(myAccountId), null);
        if (priv == null) throw new IllegalStateException("No chat key on this phone");

        Cipher rsa = Cipher.getInstance(RSA_MODE);
        rsa.init(Cipher.DECRYPT_MODE, priv, OAEP);
        byte[] aesBytes = rsa.doFinal(unb64(iSentIt ? p.k_from : p.k_to));

        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesBytes, "AES"), new GCMParameterSpec(128, unb64(p.iv)));
        return new String(gcm.doFinal(unb64(p.ct)), StandardCharsets.UTF_8);
    }

    private static byte[] lockKey(SecretKey aes, String publicKeyB64) throws Exception {
        PublicKey pub = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(unb64(publicKeyB64)));
        Cipher rsa = Cipher.getInstance(RSA_MODE);
        rsa.init(Cipher.ENCRYPT_MODE, pub, OAEP);
        return rsa.doFinal(aes.getEncoded());
    }

    private static String b64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private static byte[] unb64(String s) {
        return Base64.decode(s, Base64.NO_WRAP);
    }
}