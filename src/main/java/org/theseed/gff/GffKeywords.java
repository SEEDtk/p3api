package org.theseed.gff;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * This class represents a genome loaded from an NCBI GFF dump and a FASTA file.
 *
 * @author Bruce Parrello
 *
 */
public class GffKeywords {

    /** key/value pattern matcher */
    public static final Pattern KEY_VALUE = Pattern.compile("([^=\\s]+)\\s*=\\s*(.+)");

    /**
     * @return a map of keyword names to values from a GFF keyword field
     *
     * @param keywordField	GFF3 keyword field to parse
     */
    public static Map<String, String> gffParse(String keywordField) {
        Map<String, String> retVal = new HashMap<String, String>();
        // Split on semicolons to get the keyword-value pairs.
        String[] pieces = StringUtils.split(keywordField, ';');
        // Loop through the pieces.  For each we need the keyword and the value.  The value must be
        // URLdecoded, which requires try/catch protection.
        try {
            for (String piece : pieces) {
                Matcher m = KEY_VALUE.matcher(piece);
                if (m.matches()) {
                    String key = m.group(1);
                    String value = StringUtils.trimToEmpty(URLDecoder.decode(m.group(2), "UTF-8"));
                    // If this is a Dbxref, we need further parsing.
                    if (key.contentEquals("Dbxref")) {
                        String[] dbPieces = StringUtils.split(value, ":", 2);
                        retVal.put(dbPieces[0], dbPieces[1]);
                    } else {
                        // Here we have a normal key-value pair.
                        retVal.put(key, value);
                    }
                }
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Error decoding GFF keyword string: " + e.getMessage());
        }
        return retVal;
    }

}