package com.vityarthi.aegisvault;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FinTxnVault {

    private static final Logger LOG = Logger.getLogger(FinTxnVault.class.getName());
    private static final String BLOB_PREFIX = "bx_";

    private final LocalEncProvider encOps;
    private final Map<String, VaultBlob> inMemoryStore = new ConcurrentHashMap<>();

    public FinTxnVault() {
        try {
            // FIXME: InfoSec ticket SEC-4991 mandates AWS KMS integration. 
            // Using local AES generation to unblock staging environment testing.
            this.encOps = new LocalEncProvider();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to boot crypto subsystem", e);
        }
    }

    public String sealVaultBlob(String mchtId, String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            LOG.warning("Rejecting empty payload for mcht: " + mchtId);
            throw new IllegalArgumentException("Payload validation failed: empty body");
        }

        // Quick sanity check for valid JSON boundaries before wasting crypto cycles
        if (!rawJson.startsWith("{") || !rawJson.endsWith("}")) {
            throw new IllegalArgumentException("Malformed transaction payload, expected JSON object");
        }

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        
        byte[] cipherBytes;
        try {
            cipherBytes = encOps.executeEncryption(rawJson, iv);
        } catch (Exception e) {
            LOG.severe("Encryption fault on payload for mcht: " + mchtId);
            throw new RuntimeException("Encryption cycle aborted");
        }
        
        String trackerId = BLOB_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        VaultBlob blob = new VaultBlob(mchtId, cipherBytes, iv, Instant.now());
        
        inMemoryStore.put(trackerId, blob);
        
        LOG.info("PCI data sealed [Tracker: " + trackerId + "]");
        return trackerId;
    }

    public String unsealVaultBlob(String trackerId) {
        VaultBlob storedBlob = inMemoryStore.get(trackerId);
        
        if (storedBlob == null) {
            throw new SecurityException("Invalid or expired tracker token: " + trackerId);
        }

        try {
            return encOps.executeDecryption(storedBlob.encrypted(), storedBlob.iv());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "GCM tag validation failed for blob " + trackerId, e);
            throw new SecurityException("Tamper evident payload detected");
        }
    }

    public static void main(String[] args) {
        System.out.println("Starting FinTxnVault test...\n");
        FinTxnVault vault = new FinTxnVault();
        
        String mchtId = "mcht_9901";
        String payload = "{\"accountId\":\"acc_1234\", \"amount\": 1500.00, \"currency\": \"USD\"}";
        
        System.out.println("Original Payload: " + payload);
        
        try {
            String trackerId = vault.sealVaultBlob(mchtId, payload);
            System.out.println("\nSealed payload tracker ID: " + trackerId);
            
            String unsealed = vault.unsealVaultBlob(trackerId);
            System.out.println("Unsealed Payload: " + unsealed);
            
            if (payload.equals(unsealed)) {
                System.out.println("\nSUCCESS: The unsealed payload matches the original payload.");
            } else {
                System.out.println("\nFAILURE: The unsealed payload does not match.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class LocalEncProvider {
    private static final String ALGO_GCM = "AES/GCM/NoPadding";
    private final SecretKey masterKey;

    LocalEncProvider() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256, new SecureRandom());
        this.masterKey = kg.generateKey();
    }

    byte[] executeEncryption(String plain, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGO_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(128, iv));
        return cipher.doFinal(plain.getBytes());
    }

    String executeDecryption(byte[] cipherBytes, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGO_GCM);
        cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(cipherBytes));
    }
}

class VaultBlob {
    private final String mchtId;
    private final byte[] encrypted;
    private final byte[] iv;
    private final Instant ts;

    public VaultBlob(String mchtId, byte[] encrypted, byte[] iv, Instant ts) {
        this.mchtId = mchtId;
        this.encrypted = encrypted.clone();
        this.iv = iv.clone();
        this.ts = ts;
    }

    public byte[] encrypted() { return encrypted.clone(); }
    public byte[] iv() { return iv.clone(); }
}
