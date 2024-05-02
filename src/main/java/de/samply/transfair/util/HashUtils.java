package de.samply.transfair.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

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
public static String calculateHash(Object obj) {
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
}
