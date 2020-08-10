package no.steras.opensamlbook.sp;

import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import no.steras.opensamlbook.OpenSAMLUtils;
import no.steras.opensamlbook.idp.IDPConstants;
import org.joda.time.DateTime;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.config.XMLObjectProviderInitializer;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.messaging.context.SAMLEndpointContext;
import org.opensaml.saml.common.messaging.context.SAMLPeerEntityContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPRedirectDeflateEncoder;
import org.opensaml.saml.saml2.core.*;
import org.opensaml.saml.saml2.metadata.Endpoint;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Provider;
import java.security.Security;

/**
 * The filter intercepts the user and start the SAML authentication if it is not authenticated
 */
public class AccessFilter implements Filter {
    private static Logger logger = LoggerFactory.getLogger(AccessFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        for (Provider jceProvider : Security.getProviders()) {
            logger.info(jceProvider.getInfo());
        }

        try {
            logger.info("Bootstrapping");
            InitializationService.initialize();
            new XMLObjectProviderInitializer().init();
        } catch (InitializationException e) {
            throw new RuntimeException("Bootstrapping failed");
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest)request;
        HttpServletResponse httpServletResponse = (HttpServletResponse)response;

        if (httpServletRequest.getSession().getAttribute(SPConstants.AUTHENTICATED_SESSION_ATTRIBUTE) != null) {
            chain.doFilter(request, response);
        } else {
            setGotoURLOnSession(httpServletRequest);
            redirectUserForAuthentication(httpServletResponse);
        }
    }

    private void setGotoURLOnSession(HttpServletRequest request) {
        request.getSession().setAttribute(SPConstants.GOTO_URL_SESSION_ATTRIBUTE, request.getRequestURL().toString());
    }

    private void redirectUserForAuthentication(HttpServletResponse httpServletResponse) {
        AuthnRequest authnRequest = buildAuthnRequest();
        redirectUserWithRequest(httpServletResponse, authnRequest);

    }

    private void redirectUserWithRequest(HttpServletResponse httpServletResponse, AuthnRequest authnRequest) {
        logger.info("AuthnRequest: ");
        OpenSAMLUtils.logSAMLObject(authnRequest);
        logger.info("Redirecting to IDP");

        // No response adapters needed anymore; the response now gets set directly on the encoder
        httpServletResponse.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);

        // check your makeSamlMessageContext() method to see if any other properties on messageContext need to be set here
        MessageContext<SAMLObject> messageContext = new MessageContext<SAMLObject>();
        messageContext.setMessage(authnRequest);

        // Endpoint is now set via subcontexts
        SAMLPeerEntityContext peerEntityContext = messageContext.getSubcontext(SAMLPeerEntityContext.class, true);
        SAMLEndpointContext endpointContext = peerEntityContext.getSubcontext(SAMLEndpointContext.class, true);
        endpointContext.setEndpoint(getIDPEndpoint());

        // MessageContext and HttpServletResponse now get set directly on the encoder
        HTTPRedirectDeflateEncoder httpRedirectDeflateEncoder = new HTTPRedirectDeflateEncoder();
        httpRedirectDeflateEncoder.setMessageContext(messageContext);
        httpRedirectDeflateEncoder.setHttpServletResponse(httpServletResponse);
        try {
            httpRedirectDeflateEncoder.initialize();
            httpRedirectDeflateEncoder.encode();
        } catch (MessageEncodingException e) {
            throw new RuntimeException(e);
        } catch (ComponentInitializationException e) {
            throw new RuntimeException(e);
        }
    }

    private AuthnRequest buildAuthnRequest() {
        AuthnRequest authnRequest = OpenSAMLUtils.buildSAMLObject(AuthnRequest.class);
        authnRequest.setIssueInstant(new DateTime());
        authnRequest.setDestination(getIDPSSODestination());
        authnRequest.setProtocolBinding(SAMLConstants.SAML2_POST_BINDING_URI);
        authnRequest.setAssertionConsumerServiceURL(getAssertionConsumerEndpoint());
        authnRequest.setID(OpenSAMLUtils.generateSecureRandomId());
        authnRequest.setIssuer(buildIssuer());
        authnRequest.setNameIDPolicy(buildNameIdPolicy());
        authnRequest.setRequestedAuthnContext(buildRequestedAuthnContext());

        return authnRequest;
    }
    private RequestedAuthnContext buildRequestedAuthnContext() {
        RequestedAuthnContext requestedAuthnContext = OpenSAMLUtils.buildSAMLObject(RequestedAuthnContext.class);
        requestedAuthnContext.setComparison(AuthnContextComparisonTypeEnumeration.MINIMUM);

        AuthnContextClassRef passwordAuthnContextClassRef = OpenSAMLUtils.buildSAMLObject(AuthnContextClassRef.class);
        passwordAuthnContextClassRef.setAuthnContextClassRef(AuthnContext.PASSWORD_AUTHN_CTX);

        requestedAuthnContext.getAuthnContextClassRefs().add(passwordAuthnContextClassRef);

        return requestedAuthnContext;

    }

    private NameIDPolicy buildNameIdPolicy() {
        NameIDPolicy nameIDPolicy = OpenSAMLUtils.buildSAMLObject(NameIDPolicy.class);
        nameIDPolicy.setAllowCreate(true);

        nameIDPolicy.setFormat(NameIDType.TRANSIENT);

        return nameIDPolicy;
    }

    private Issuer buildIssuer() {
        Issuer issuer = OpenSAMLUtils.buildSAMLObject(Issuer.class);
        issuer.setValue(getSPIssuerValue());

        return issuer;
    }

    private String getSPIssuerValue() {
        return SPConstants.SP_ENTITY_ID;
    }

    private String getSPNameQualifier() {
        return SPConstants.SP_ENTITY_ID;
    }

    private String getAssertionConsumerEndpoint() {
        return SPConstants.ASSERTION_CONSUMER_SERVICE;
    }

    private String getIDPSSODestination() {
        return IDPConstants.SSO_SERVICE;
    }

    private Endpoint getIDPEndpoint() {
        SingleSignOnService endpoint = OpenSAMLUtils.buildSAMLObject(SingleSignOnService.class);
        endpoint.setBinding(SAMLConstants.SAML2_REDIRECT_BINDING_URI);
        endpoint.setLocation(getIDPSSODestination());

        return endpoint;
    }


    @Override
    public void destroy() {

    }
}