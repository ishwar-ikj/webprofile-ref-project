package no.steras.opensamlbook.sp;

import net.shibboleth.utilities.java.support.xml.BasicParserPool;
import no.steras.opensamlbook.OpenSAMLUtils;
import no.steras.opensamlbook.idp.IDPConstants;
import no.steras.opensamlbook.idp.IDPCredentials;
import org.apache.http.impl.client.HttpClientBuilder;
import org.joda.time.DateTime;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.profile.context.ProfileRequestContext;
import org.opensaml.saml.saml2.core.*;
import org.opensaml.saml.saml2.encryption.Decrypter;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.soap.client.http.HttpSOAPClient;
import org.opensaml.soap.messaging.context.SOAP11Context;
import org.opensaml.soap.soap11.Envelope;
import org.opensaml.xmlsec.encryption.support.DecryptionException;
import org.opensaml.xmlsec.encryption.support.InlineEncryptedKeyResolver;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.opensaml.xmlsec.signature.support.Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by Privat on 4/6/14.
 */
public class ConsumerServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(ConsumerServlet.class);

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        logger.info("Artifact received");
        Artifact artifact = buildArtifactFromRequest(req);
        logger.info("Artifact: " + artifact.getArtifact());

        ArtifactResolve artifactResolve = buildArtifactResolve(artifact);
        signArtifactResolve(artifactResolve);
        logger.info("Sending ArtifactResolve");
        logger.info("ArtifactResolve: ");
        OpenSAMLUtils.logSAMLObject(artifactResolve);

        ArtifactResponse artifactResponse = sendAndReceiveArtifactResolve(artifactResolve);
        logger.info("ArtifactResponse received");
        logger.info("ArtifactResponse: ");
        OpenSAMLUtils.logSAMLObject(artifactResponse);

        EncryptedAssertion encryptedAssertion = getEncryptedAssertion(artifactResponse);
        Assertion assertion = decryptAssertion(encryptedAssertion);
        verifyAssertionSignature(assertion);
        logger.info("Decrypted Assertion: ");
        OpenSAMLUtils.logSAMLObject(assertion);

        logAssertionAttributes(assertion);
        logAuthenticationInstant(assertion);
        logAuthenticationMethod(assertion);

        setAuthenticatedSession(req);
        redirectToGotoURL(req, resp);
    }

    private Assertion decryptAssertion(EncryptedAssertion encryptedAssertion) {
        StaticKeyInfoCredentialResolver keyInfoCredentialResolver = new StaticKeyInfoCredentialResolver(SPCredentials.getCredential());

        Decrypter decrypter = new Decrypter(null, keyInfoCredentialResolver, new InlineEncryptedKeyResolver());
        decrypter.setRootInNewDocument(true);

        try {
            return decrypter.decrypt(encryptedAssertion);
        } catch (DecryptionException e) {
            throw new RuntimeException(e);
        }
    }

    private void verifyAssertionSignature(Assertion assertion) {
        if (!assertion.isSigned()) {
            throw new RuntimeException("The SAML Assertion was not signed");
        }

        try {
            SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
            profileValidator.validate(assertion.getSignature());

            SignatureValidator.validate(assertion.getSignature(), IDPCredentials.getCredential());

            logger.info("SAML Assertion signature verified");
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }

    }

    private void signArtifactResolve(ArtifactResolve artifactResolve) {
        Signature signature = OpenSAMLUtils.buildSAMLObject(Signature.class);
        signature.setSigningCredential(SPCredentials.getCredential());
        signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA1);
        signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

        artifactResolve.setSignature(signature);

        try {
            XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(artifactResolve).marshall(artifactResolve);
        } catch (MarshallingException e) {
            throw new RuntimeException(e);
        }

        try {
            Signer.signObject(signature);
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    private void setAuthenticatedSession(HttpServletRequest req) {
        req.getSession().setAttribute(SPConstants.AUTHENTICATED_SESSION_ATTRIBUTE, true);
    }

    private void redirectToGotoURL(HttpServletRequest req, HttpServletResponse resp) {
        String gotoURL = (String)req.getSession().getAttribute(SPConstants.GOTO_URL_SESSION_ATTRIBUTE);
        logger.info("Redirecting to requested URL: " + gotoURL);
        try {
            resp.sendRedirect(gotoURL);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void logAuthenticationMethod(Assertion assertion) {
        logger.info("Authentication method: " + assertion.getAuthnStatements().get(0)
                .getAuthnContext().getAuthnContextClassRef().getAuthnContextClassRef());
    }

    private void logAuthenticationInstant(Assertion assertion) {
        logger.info("Authentication instant: " + assertion.getAuthnStatements().get(0).getAuthnInstant());
    }

    private void logAssertionAttributes(Assertion assertion) {
        for (Attribute attribute : assertion.getAttributeStatements().get(0).getAttributes()) {
            logger.info("Attribute name: " + attribute.getName());
            for (XMLObject attributeValue : attribute.getAttributeValues()) {
                logger.info("Attribute value: " + ((XSString) attributeValue).getValue());
            }
        }
    }

    private EncryptedAssertion getEncryptedAssertion(ArtifactResponse artifactResponse) {
        Response response = (Response)artifactResponse.getMessage();
        return response.getEncryptedAssertions().get(0);
    }

    private ArtifactResponse sendAndReceiveArtifactResolve(final ArtifactResolve artifactResolve) {
        try {
            Envelope envelope = OpenSAMLUtils.wrapInSOAPEnvelope(artifactResolve);

            // build the soap client
            BasicParserPool basicParserPool = new BasicParserPool();
            basicParserPool.initialize();
            HttpSOAPClient soapClient = new HttpSOAPClient();
            soapClient.setParserPool(basicParserPool);
            soapClient.setHttpClient(HttpClientBuilder.create().build());

            // Build the outbound context
            SOAP11Context soap11Context = new SOAP11Context();
            soap11Context.setEnvelope(envelope);
            MessageContext<XMLObject> messageContext = new MessageContext<XMLObject>();
            messageContext.addSubcontext(soap11Context);
            ProfileRequestContext<XMLObject, XMLObject> profileRequestContext = new ProfileRequestContext<XMLObject, XMLObject>();
            profileRequestContext.setOutboundMessageContext(messageContext);

            // Call IDP
            soapClient.send(IDPConstants.ARTIFACT_RESOLUTION_SERVICE, profileRequestContext);

            // Parse the response
            MessageContext<XMLObject> inboundContext = profileRequestContext.getInboundMessageContext();
            if (inboundContext == null || inboundContext.getSubcontext(SOAP11Context.class, false) == null) {
                throw new Exception("null response from IDP resolution request");
            }
            Envelope soapResponse = inboundContext.getSubcontext(SOAP11Context.class, false).getEnvelope();

            logger.info("RESPONSE from IDP");
            OpenSAMLUtils.logSAMLObject(soapResponse);
            if (soapResponse == null || soapResponse.getBody() == null || soapResponse.getBody().getUnknownXMLObjects().size() == 0) {
                throw new Exception("null response from IDP resolution request");
            }
            return (ArtifactResponse)soapResponse.getBody().getUnknownXMLObjects().get(0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Artifact buildArtifactFromRequest(final HttpServletRequest req) {
        Artifact artifact = OpenSAMLUtils.buildSAMLObject(Artifact.class);
        artifact.setArtifact(req.getParameter("SAMLart"));
        return artifact;
    }

    private ArtifactResolve buildArtifactResolve(final Artifact artifact) {
        ArtifactResolve artifactResolve = OpenSAMLUtils.buildSAMLObject(ArtifactResolve.class);

        Issuer issuer = OpenSAMLUtils.buildSAMLObject(Issuer.class);
        issuer.setValue(SPConstants.SP_ENTITY_ID);
        artifactResolve.setIssuer(issuer);

        artifactResolve.setIssueInstant(new DateTime());

        artifactResolve.setID(OpenSAMLUtils.generateSecureRandomId());

        artifactResolve.setDestination(IDPConstants.ARTIFACT_RESOLUTION_SERVICE);

        artifactResolve.setArtifact(artifact);

        return artifactResolve;
    }

}
