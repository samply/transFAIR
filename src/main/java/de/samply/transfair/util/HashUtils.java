package de.samply.transfair.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

public class HashUtils {
/**
 * Calculates a hash value for the given object using MD2 algorithm and encodes it in a format suitable for use as an ID.
 * The hash is first serialized to a byte array, then processed through the MD2 hashing algorithm, and finally encoded
 * using URL-safe Base64 encoding. Any hyphens or underscores in the encoded string are replaced with zeros, and
 * trailing equals signs are removed.
 *
 * @param obj The object to be hashed. Must not be null.
 * @return A string representation of the calculated hash.
*/
public static String generateHashFromObject(Object obj) {
        String returnVal = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(obj);
            oos.close();
            MessageDigest digest = MessageDigest.getInstance("MD2");
            byte[] hashBytes = digest.digest(baos.toByteArray());
            // encode into a format that Blaze will accept as an ID
            returnVal = Base64.getUrlEncoder().encodeToString(hashBytes).replaceAll("[-_]", "0").replaceAll("=+$", "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        return returnVal;
    }

    /**
     * Generates a hash from a list of strings.
     *
     * @param strings A list of strings to be concatenated and hashed.
     * @return A hexadecimal string representing the hash of the concatenated input strings.
     */
    public static String generateHashFromStringList(List<String> strings) {
        try {
//            MessageDigest md = MessageDigest.getInstance("SHA-256");
            MessageDigest md = MessageDigest.getInstance("MD2");
            StringBuilder sb = new StringBuilder();
            for (String s : strings) {
                sb.append(s);
            }
            String input = sb.toString();
            byte[] bytes = input.getBytes();
            byte[] hashBytes = md.digest(bytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
