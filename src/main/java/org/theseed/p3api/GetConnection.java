/**
 *
 */
package org.theseed.p3api;

import org.apache.http.client.fluent.Request;

/**
 * This is an alternate version of the connection object that uses GET instead of POST.  With GET, you
 * cannot have as many input parameters, but it avoids a bug with searches in multi-valued fields.
 * @author Bruce Parrello
 *
 */
public class GetConnection extends Connection {

    /**
     * Initialize a connection.
     *
     * @param token		security token for the desired user
     */
    public GetConnection(String token) {
        super(token);
    }

    /**
     * Initialize a connection for the currently logged-in user.
     */
    public GetConnection() {
        super();
    }

    @Override
    protected Request createRequest(String table) {
        return Request.Post(super.url + table);
    }

}
