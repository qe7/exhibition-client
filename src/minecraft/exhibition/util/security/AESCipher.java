package exhibition.util.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AesCipher
 * <p>Encode/Decode text by password using AES-128-CBC algorithm</p>
 */
public class AESCipher {
    public static final int INIT_VECTOR_LENGTH = 16;
    /**
     * @see <a href="https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java">how-to-convert-a-byte-array-to-a-hex-string</a>
     */
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    /**
     * Encoded/Decoded data
     */
    protected String data;
    /**
     * Initialization vector value
     */
    protected String initVector;
    /**
     * Error message if operation failed
     */
    protected String errorMessage;

    /**
     * AesCipher constructor.
     *
     * @param initVector   Initialization vector value
     * @param data         Encoded/Decoded data
     * @param errorMessage Error message if operation failed
     */
    private AESCipher(String initVector,String data, String errorMessage) {
        super();

        this.initVector = initVector;
        this.data = data;
        this.errorMessage = errorMessage;
    }

    /**
     * Encrypt input text by AES-128-CBC algorithm
     *
     * @param secretKey 16/24/32 -characters secret password
     * @param plainText Text for encryption
     * @return Encoded string or NULL if error
     */
    public static AESCipher encrypt(String secretKey, String plainText) {
        String initVector = null;
        try {
            // Check secret length
            if (!isKeyLengthValid(secretKey)) {
                throw new Exception("Secret key's length must be 128, 192 or 256 bits");
            }

            // Get random initialization vector
            SecureRandom secureRandom = new SecureRandom();
            byte[] initVectorBytes = new byte[INIT_VECTOR_LENGTH / 2];
            secureRandom.nextBytes(initVectorBytes);
            initVector = bytesToHex(initVectorBytes);
            initVectorBytes = initVector.getBytes(StandardCharsets.UTF_8);

            IvParameterSpec ivParameterSpec = new IvParameterSpec(initVectorBytes);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);

            // Encrypt input text
            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));

            ByteBuffer byteBuffer = ByteBuffer.allocate(initVectorBytes.length + encrypted.length);
            byteBuffer.put(initVectorBytes);
            byteBuffer.put(encrypted);

            // Result is base64-encoded string: initVector + encrypted result
            String result = AuthenticationUtil.decodeByteArray(Base64.getEncoder().encode(byteBuffer.array()));

            // Return successful encoded object
            return new AESCipher(initVector, result, null);
        } catch (Throwable t) {
            t.printStackTrace();
            // Operation failed
            return new AESCipher(initVector, null, t.getMessage());
        }
    }

    /**
     * Decrypt encoded text by AES-128-CBC algorithm
     *
     * @param secretKey  16/24/32 -characters secret password
     * @param cipherText Encrypted text
     * @return Self object instance with data or error message
     */
    public static AESCipher decrypt(String secretKey, String cipherText) {
        try {
            // Check secret length
            if (!isKeyLengthValid(secretKey)) {
                throw new Exception("Secret key's length must be 128, 192 or 256 bits");
            }

            // Get raw encoded data
            byte[] encrypted = Base64.getDecoder().decode(cipherText.getBytes());

            // Slice initialization vector
            IvParameterSpec ivParameterSpec = new IvParameterSpec(encrypted, 0, INIT_VECTOR_LENGTH);
            // Set secret password
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            // Trying to get decrypted text
            String result = new String(cipher.doFinal(encrypted, INIT_VECTOR_LENGTH, encrypted.length - INIT_VECTOR_LENGTH));

            // Return successful decoded object
            return new AESCipher(bytesToHex(ivParameterSpec.getIV()), result, null);
        } catch (Throwable t) {
            t.printStackTrace();
            // Operation failed
            return new AESCipher(null, null, t.getMessage());
        }
    }

    /**
     * Decrypt encoded text by AES-128-CBC algorithm
     *
     * @param secretKey  16/24/32 -characters secret password
     * @param cipherText Encrypted text
     * @return Self object instance with data or error message
     */
    public static byte[] decryptToByteArray(String secretKey, String cipherText) {
        try {
            // Get raw encoded data
            byte[] encrypted = Base64.getDecoder().decode(cipherText.getBytes());

            // Slice initialization vector
            IvParameterSpec ivParameterSpec = new IvParameterSpec(encrypted, 0, INIT_VECTOR_LENGTH);

            byte[] decryptionKey = new byte[INIT_VECTOR_LENGTH];

            // "ECCNygH68stZA84Q"

            for(int i = 0; i < 16; i++) {
                if(i == 0) {
                    decryptionKey[i] = HWIDCheck._43().substring(0, 1).getBytes()[i];
                } else if(i <= 2) {
                    decryptionKey[i] = HWIDCheck._17().substring(0,1).getBytes()[0];
                } else if(i == 3) {
                    decryptionKey[i] = 0x4e;
                } else if(i == 4) {
                    decryptionKey[i] = 0x79;
                } else {
                    decryptionKey[i] = secretKey.getBytes()[i - 5];
                }
            }

            // Set secret password
            SecretKeySpec secretKeySpec = new SecretKeySpec(decryptionKey, "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            // Return successful decoded object
            return cipher.doFinal(encrypted, INIT_VECTOR_LENGTH, encrypted.length - INIT_VECTOR_LENGTH);
        } catch (Throwable t) {
            t.printStackTrace();
            // Operation failed
            return null;
        }
    }

    /**
     * Check that secret password length is valid
     *
     * @param key 16/24/32 -characters secret password
     * @return TRUE if valid, FALSE otherwise
     */
    public static boolean isKeyLengthValid(String key) {
        return key.length() == 16 || key.length() == 24 || key.length() == 32;
    }

    /**
     * Convert Bytes to HEX
     *
     * @param bytes Bytes array
     * @return String with bytes values
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Get encoded/decoded data
     */
    public String getData() {
        return data;
    }

    /**
     * Get initialization vector value
     */
    public String getInitVector() {
        return initVector;
    }

    /**
     * Get error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Check that operation failed
     *
     * @return TRUE if failed, FALSE otherwise
     */
    public boolean hasError() {
        return this.errorMessage != null;
    }

    /**
     * To string return resulting data
     *
     * @return Encoded/decoded data
     */
    public String toString() {
        return getData();
    }
}

