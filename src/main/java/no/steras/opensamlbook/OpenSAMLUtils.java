package no.steras.opensamlbook;

import net.shibboleth.utilities.java.support.security.SecureRandomIdentifierGenerationStrategy;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.common.SignableSAMLObject;
import org.opensaml.soap.soap11.Body;
import org.opensaml.soap.soap11.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

/**
 * Created by Privat on 4/6/14.
 */
public class OpenSAMLUtils {
    private static Logger logger = LoggerFactory.getLogger(OpenSAMLUtils.class);
    private static SecureRandomIdentifierGenerationStrategy secureRandomIdGenerator;

    static {
        secureRandomIdGenerator = new SecureRandomIdentifierGenerationStrategy();
    }

    public static <T extends XMLObject> T buildSAMLObject(final Class<T> clazz) {
        T object = null;
        try {
            XMLObjectBuilderFactory builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory();
            QName defaultElementName = (QName)clazz.getDeclaredField("DEFAULT_ELEMENT_NAME").get(null);
            object = (T)builderFactory.getBuilder(defaultElementName).buildObject(defaultElementName);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Could not create SAML object");
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Could not create SAML object");
        }

        return object;
    }

    public static String generateSecureRandomId() {
        return secureRandomIdGenerator.generateIdentifier();
    }

    public static String encodeSAMLObject(final XMLObject object) throws MarshallingException, TransformerException {
        Element element = null;

        if (object instanceof SignableSAMLObject && ((SignableSAMLObject)object).isSigned() && object.getDOM() != null) {
            element = object.getDOM();
        } else {
            Marshaller out = XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(object);
            out.marshall(object);
            element = object.getDOM();
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        // transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StreamResult result = new StreamResult(new StringWriter());
        DOMSource source = new DOMSource(element);

        transformer.transform(source, result);
        String resultString = result.getWriter().toString();
        logger.info("encoded Saml object to");
        logger.info(resultString);
        return resultString;
    }

    public static void logSAMLObject(final XMLObject object) {
        try {
            encodeSAMLObject(object);
        } catch (MarshallingException | TransformerException e) {
            e.printStackTrace();
        }
    }

    public static Envelope wrapInSOAPEnvelope(final XMLObject xmlObject) {
        Envelope envelope = OpenSAMLUtils.buildSAMLObject(Envelope.class);
        Body body = OpenSAMLUtils.buildSAMLObject(Body.class);

        body.getUnknownXMLObjects().add(xmlObject);

        envelope.setBody(body);

        return envelope;
    }
}
