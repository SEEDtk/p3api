package org.theseed.p3api;

import java.util.List;

import org.apache.http.client.fluent.Request;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This is a subclass of SolrConnection that uses cursor-based paging, which is more efficient
 * for large result sets. The drawback is that the user cannot control the sort order, and
 * the query does not use the standard query format, but rather the raw SOLR format.
 * 
 * @author Bruce Parrello
 */
public class CursorConnection extends SolrConnection {

    @Override
    protected Request createRequest(String tableName) {
        // TODO CursorConnection create request
        throw new UnsupportedOperationException("Unimplemented method 'createRequest'");
    }

    @Override
	protected List<JsonObject> getResponse(Request request) {
        // TODO CursorConnection get response using cursor
        throw new UnsupportedOperationException("Unimplemented method 'getResponse'");
    }

}
