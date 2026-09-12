# aegisvault
A lightweight, zero-dependency Java utility for sealing and unsealing financial transaction payloads.

Basically, it takes raw JSON payloads from merchants, encrypts them using AES-256-GCM, and spits out a safe, short tracker ID (bx_...) that you can pass around the system without leaking sensitive PCI data in our application logs.

Setup & Execution

This project is currently just a raw Java implementation with zero external dependencies, so you don't need Maven or Gradle to get it running locally.

1. Prerequisites

Java Development Kit (JDK): You need JDK 8 or higher installed on your machine.

Check your installation by running:

java -version
javac -version


2. Getting the Code

Since this is a single-file utility right now, just pull down the repository or copy the FinTxnVault.java file into a local directory.

Make sure your directory structure matches the package name if you plan to use it in a larger project, but for local testing, putting it in any folder is fine.

3. Compilation

Open your terminal, navigate to the folder containing the file, and compile it:

javac FinTxnVault.java


Note: This will generate a few .class files in the directory (FinTxnVault.class, LocalEncProvider.class, and VaultBlob.class).

4. Running the Test Program

I included a quick main method in the class so you can easily verify that the encryption and decryption cycles are working on your machine.

Run the compiled class using:

java FinTxnVault


(If you placed it inside a strict folder structure matching the com.vityarthi.aegisvault package, you'll need to run java com.vityarthi.aegisvault.FinTxnVault from the root directory).

Expected Output:
You should see it spit out the original payload, the generated bx_... tracker ID, the unsealed payload, and a SUCCESS message confirming the match.

Usage in Code

If you're wiring this into another service, the API is pretty simple:

FinTxnVault vault = new FinTxnVault();

// 1. Seal a payload
String payload = "{\"accountId\":\"acc_1234\", \"amount\": 1500.00}";
String trackerId = vault.sealVaultBlob("mcht_9901", payload);
// Returns something like: bx_a1b2c3d4e5f6

// 2. Unseal it later
String originalJson = vault.unsealVaultBlob(trackerId);


Tech Debt & Important Notes

Please read this before trying to wire this into anything headed for production.

Pending AWS KMS Integration (Ticket SEC-4991): Right now, LocalEncProvider is just spinning up a local AES key on boot. This is only to unblock staging environment testing. Do not use this in production yet. We need to swap the local provider out for AWS KMS before this goes live to comply with InfoSec.

Ephemeral Storage: The encrypted blobs are currently stored in a ConcurrentHashMap (inMemoryStore). If the JVM restarts or crashes, all sealed data is permanently gone. We will eventually need to back this with Redis or a persistent database.

Validation: The sealVaultBlob method does a quick-and-dirty sanity check to ensure the string starts/ends with { and } before wasting CPU cycles on cryptography. It does not do deep JSON parsing, so ensure the upstream services aren't passing garbage.
