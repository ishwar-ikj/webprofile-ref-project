package no.steras.opensamlbook.sp;

import org.opensaml.security.credential.BasicCredential;
import org.opensaml.security.credential.Credential;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * Created by Privat on 13/05/14.
 */
public class SPCredentials {
    private static final Credential credential;

    static {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            KeyPair keyPair = kpg.generateKeyPair();
            credential = new BasicCredential(keyPair.getPublic(), keyPair.getPrivate());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static Credential getCredential() {
        return credential;
    }
}
