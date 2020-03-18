/**
 *
 */
package org.theseed.gff;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.theseed.sequence.Sequence;

/**
 * This class parses the keyword field in a GFF3 file.  Value fields are decoded; extra spaces and
 * fake parentheses are removed.
 *
 * @author Bruce Parrello
 *
 */
public class ViprKeywords {

    /** key/value pattern matcher */
    public static final Pattern KEY_VALUE = Pattern.compile("([^=\\s]+)\\s*=\\s*(.+)");

    /**
     * @return a map of keyword names to values from a Vipr GFF keyword field
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
                    // Now we need to handle the extra-parenthesis error in ViPR's GFF formatting.
                    while (value.endsWith(")") &&
                            StringUtils.countMatches(value, ')') - StringUtils.countMatches(value, '(') > 0)
                        value = StringUtils.chop(value);
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

    /**
     * @return a map of keyword names to values for a ViPR FASTA sequence
     *
     * @param fastaSeq	a FASTA sequence from a ViPR FASTA file
     */
    public static Map<String, String> fastaParse(Sequence fastaSeq) {
        Map<String, String> retVal = new HashMap<String, String>();
        // Reform the label and comment into a single string.
        String keywords = fastaSeq.getLabel() + " " + fastaSeq.getComment();
        // Split it on the vertical bar.
        String[] pieces = StringUtils.split(keywords, '|');
        for (String piece : pieces) {
            // The key and value are separated by a colon.
            String[] dbPieces = StringUtils.split(piece, ":", 2);
            retVal.put(dbPieces[0], dbPieces[1]);
        }
        return retVal;
    }
}
