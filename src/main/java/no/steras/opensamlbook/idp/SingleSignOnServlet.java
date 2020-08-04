package no.steras.opensamlbook.idp;

import no.steras.opensamlbook.OpenSAMLUtils;
import org.apache.xml.security.utils.Base64;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.saml2.core.ArtifactResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.Writer;

/**
 * Created by Privat on 4/6/14.
 */
public class SingleSignOnServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(SingleSignOnServlet.class);
    private static final String ASSERTION_CONSUMER_SERVICE = "http://localhost:8080/webprofile-ref-project/sp/consumer";


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        logger.info("AuthnRequest recieved");
        Writer w = resp.getWriter();
        resp.setContentType("text/html");
        w.append("<html>" + "<head></head>" + "<body><h1>You are now at IDP, click the button to authenticate</h1> <form method=\"POST\">"
                + "<input type=\"submit\" value=\"Authenticate\"/>" + "</form>" + "</body>" + "</html>");
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        ArtifactResponse artifactResponse = new ArtifactResolutionServlet().buildArtifactResponse();
        String encodedArtifact = null;
        try {
            encodedArtifact = Base64.encode(OpenSAMLUtils.encodeSAMLObject(artifactResponse).getBytes(), 0);
        } catch (MarshallingException | TransformerException e) {
            e.printStackTrace();
        }

        Writer w = resp.getWriter();
        resp.setContentType("text/html");
        w.append(
                "<html><head></head><body><h1>Redirecting to SP</h1>" +
                        "<form name = \"myform\" method=\"POST\" action=\"" + ASSERTION_CONSUMER_SERVICE + "\">"
                        + "<input type=\"hidden\" name=\"SAMLResponse\" value=\"" + encodedArtifact + "\" />"
                        + "<input type=\"submit\" value=\"Submit\"/>" + "</form>"
                        + "<script type=\"text/javascript\">\n"
                        + "document.myform.submit();\n"
                        + "</script>"
                        + "</body></html>");
    }


}
