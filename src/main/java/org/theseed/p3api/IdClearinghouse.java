/**
 *
 */
package org.theseed.p3api;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This object is used to request a new genome ID from the clearinghouse.  The clearinghouse is a simple SOAP
 * server without a WSDL designed for the PERL SOAP::Lite interface, so we have to do things manually.
 *
 * The class maintains an HTTP connection, and uses it to make register_genome requests.
 *
 * @author Bruce Parrello
 *
 */
public class IdClearinghouse {

    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(IdClearinghouse.class);

    /** default URL */
    private static final String CLEARINGHOUSE_URL = "http://clearinghouse.theseed.org/Clearinghouse/clearinghouse_services.cgi";

    /** XML prefix */
    private static final String REQUEST_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soap:Envelope soap:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:soapenc=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><soap:Body><register_genome xmlns=\"http://www.soaplite.com/Scripts\"><c-gensym3 xsi:type=\"xsd:int\">";

    /** XML suffix */
    private static final String REQUEST_SUFFIX = "</c-gensym3></register_genome></soap:Body></soap:Envelope>";

    /* SOAP action */
    private static final String SOAP_ACTION = "\"http://www.soaplite.com/Scripts#register_genome\"";

    /** maximum number of tries */
    private static final int MAX_TRIES = 10;

    /** timeout value (one minute) */
    private static final int TIMEOUT = 60 * 1000;

    /** match pattern for extracting version number */
    private static final Pattern RESULT_PATTERN = Pattern.compile("<s-gensym3 xsi:type=\"xsd:int\">(\\d+)</s-gensym3>");

    /**
     * Construct a new clearinghouse connection.
     */
    public IdClearinghouse() {	}

    /**
     * Request a new genome ID for the specified taxonomic ID.
     */
    public String computeGenomeId(int taxId) {
        String retVal = null;
        Request soapRequest = Request.Post(CLEARINGHOUSE_URL);
        // Look for a SOAP XML response.
        soapRequest.addHeader("Accept", "text/xml");
        soapRequest.addHeader("SOAPAction", SOAP_ACTION);
        // Set the request limitations.
        soapRequest.connectTimeout(TIMEOUT);
        // Build the XML body.
        HttpEntity soapBody = new StringEntity(REQUEST_PREFIX + Integer.toString(taxId) + REQUEST_SUFFIX, ContentType.TEXT_XML);
        soapRequest.body(soapBody);
        // Send the request.
        int tries = 0;
        while (tries < MAX_TRIES && retVal == null) {
            try {
                tries++;
                Response resp = soapRequest.execute();
                // Check the response.
                HttpResponse rawResponse = resp.returnResponse();
                int statusCode = rawResponse.getStatusLine().getStatusCode();
                if (statusCode < 400) {
                    try {
                        String xml = EntityUtils.toString(rawResponse.getEntity());
                        Matcher m = RESULT_PATTERN.matcher(xml);
                        if (m.find()) {
                            retVal = taxId + "." + m.group(1);
                        } else {
                            throw new RuntimeException("Invalid xml response to request for genome ID from tax ID " + taxId + ".");
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("HTTP conversion error in genome ID request:  " + e.getMessage());
                    }
                } else if (tries < MAX_TRIES) {
                    log.debug("Retrying genome ID request after error code {}.", statusCode);
                } else {
                    throw new RuntimeException("Genome ID request failed with error " + statusCode + " " +
                            rawResponse.getStatusLine().getReasonPhrase());
                }
            } catch (IOException e) {
                // This is usually a timeout error.
                if (tries >= MAX_TRIES)
                    throw new RuntimeException("HTTP error in genome ID request: " + e.getMessage());
                else {
                    tries++;
                    log.debug("Retrying genome ID request after " + e.getMessage());
                }
            }
        }
        return retVal;
    }
}
