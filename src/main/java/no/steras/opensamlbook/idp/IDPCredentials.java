package no.steras.opensamlbook.idp;

import org.opensaml.security.credential.BasicCredential;
import org.opensaml.security.credential.Credential;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * Created by Privat on 13/05/14.
 */
public class IDPCredentials {
    private static final Credential credential;

    static {
        credential = generateCredential();
    }

    private static Credential generateCredential() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            KeyPair keyPair = kpg.generateKeyPair();
            return new BasicCredential(keyPair.getPublic(), keyPair.getPrivate());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static Credential getCredential() {
        return credential;
    }
}
