/**
 *
 */
package org.theseed.gff;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.theseed.sequence.Sequence;

/**
 * This class parses the keyword fields in a ViPR files.  Value fields are decoded; extra spaces and
 * fake parentheses are removed.
 *
 * @author Bruce Parrello
 *
 */
public class ViprKeywords extends GffKeywords {

    public static Map<String, String> gffParse(String keywordField) {
        Map<String, String> retVal = GffKeywords.gffParse(keywordField);
        // Now we need to handle the extra-parenthesis error in ViPR's GFF formatting.
        for (Map.Entry<String, String> entry : retVal.entrySet()) {
            String value = entry.getValue();
            if (value.endsWith(")") &&
                    StringUtils.countMatches(value, ')') - StringUtils.countMatches(value, '(') > 0)
                entry.setValue(StringUtils.chop(value));
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
            if (dbPieces.length == 2)
                retVal.put(dbPieces[0], dbPieces[1]);
        }
        return retVal;
    }
}
