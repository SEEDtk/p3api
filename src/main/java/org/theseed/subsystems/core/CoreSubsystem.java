/**
 *
 */
package org.theseed.subsystems.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.io.LineReader;
import org.theseed.io.MarkerFile;
import org.theseed.proteins.Role;
import org.theseed.proteins.RoleMap;
import org.theseed.utils.ParseFailureException;

/**
 * A core subsystem contains various bits of information about a subsystem found in the CoreSEED.
 * This includes the name, the three classifications, the roles, the role IDs, the abbreviations,
 * the auxiliary roles, the spreadsheet rows, and the variant rules.
 *
 * @author Bruce Parrello
 *
 */
public class CoreSubsystem {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(CoreSubsystem.class);
    /** subsystem name */
    private String name;
    /** rule namespace */
    private Map<String, SubsystemRule> ruleMap;
    /** map of variant rules, in priority order */
    private LinkedHashMap<String, SubsystemRule> variantRules;
    /** version number */
    private int version;
    /** TRUE if this is a good subsystem (exchangeable and non-experimental) */
    private boolean good;
    /** auxiliary role list */
    private Set<String> auxRoles;
    /** associated role ID computation map */
    private RoleMap roleMap;
    /** list of roles, in order */
    private List<Role> roles;
    /** subsystem spreadsheet */
    private Map<String, Row> spreadsheet;
    /** classifications */
    private List<String> classes;
    /** common representation of an empty cell */
    private static final Set<String> EMPTY_CELL = Collections.emptySet();
    /** marker to separate file sections */
    private static final String SECTION_MARKER = "//";
    /** main rule parser, for separating the rule name and the text to compile */
    private static final Pattern RULE_PATTERN = Pattern.compile("\\s*(\\S+)\\s+means\\s+(.+)");

    /**
     * This class describes a spreadsheet row.  It contains the the variant code and the list of peg sets in column order.
     * An empty cell will point to an empty set of peg IDs.
     */
    public class Row {

        /** ID of the target genome */
        private String genomeId;
        /** variant code */
        private String variantCode;
        /** list of peg sets */
        private List<Set<String>> columns;

        /**
         * Construct a subsystem row from a spreadsheet line.
         *
         * @param cols		columns from the input spreadsheet line
         */
        protected Row(String[] cols) {
            // Save the genome ID and variant code.
            this.genomeId = cols[0];
            this.variantCode = cols[1];
            // Create a basic feature ID prefix.
            String prefix = "fig|" + this.genomeId + ".";
            // Create the column list.
            final int n = CoreSubsystem.this.roles.size();
            this.columns = new ArrayList<Set<String>>(CoreSubsystem.this.roles.size());
            while (this.columns.size() < CoreSubsystem.this.roles.size())
                this.columns.add(EMPTY_CELL);
            for (int i = 0; i < n; i++) {
                // Get the role column.
                final int i2 = i + 2;
                String column = (i2 >= cols.length ? "" : cols[i2]);
                if (! column.isBlank()) {
                    // Here we have to parse the pegs.  Split up the peg specifiers (there is usually only 1).
                    String[] pegSpecs = StringUtils.split(column, ',');
                    Set<String> roleSet = new TreeSet<String>();
                    for (String pegSpec : pegSpecs) {
                        if (pegSpec.contains(".")) {
                            // A dot indicates it's not a peg, and has the type included.
                            roleSet.add(prefix + pegSpec);
                        } else {
                            // Otherwise it's a real peg.
                            roleSet.add(prefix + "peg." + pegSpec);
                        }
                    }
                    // Store the role set in the column cell.
                    this.columns.set(i, roleSet);
                }
            }
        }

        /**
         * @return the genomeId
         */
        public String getGenomeId() {
            return this.genomeId;
        }

        /**
         * @return the variantCode
         */
        public String getVariantCode() {
            return this.variantCode;
        }

        /**
         * @return the list of columns
         */
        public List<Set<String>> getColumns() {
            return this.columns;
        }

    }

    /**
     * Construct a core subsystem descriptor from a subsystem directory.
     *
     * @param inDir		subsystem directory to use
     * @param roleDefs	role definitions to use
     *
     * @throws IOException
     * @throws ParseFailureException
     */
    public CoreSubsystem(File inDir, RoleMap roleDefs) throws IOException, ParseFailureException {
        // Compute the real subsystem name.
        this.name = dirToName(inDir);
        log.info("Reading subsystem {}.", this.name);
        // Save the role definitions.
        this.roleMap = roleDefs;
        // Now get the classification and version.
        this.setClassification(inDir);
        // Initialize the rule namespace and the role list.
        this.ruleMap = new HashMap<String, SubsystemRule>();
        this.roles = new ArrayList<Role>();
        // Read in the subsystem spreadsheet.  This will initialize the name space, collect the
        // rule list and auxiliary rules, and store the rows.
        this.spreadsheet = new HashMap<String, Row>();
        this.readSpreadsheet(inDir);
        // Compile the variant rules.
        this.variantRules = new LinkedHashMap<String, SubsystemRule>();
        this.readRules(inDir, "checkvariant_definitions", this.ruleMap);
        this.readRules(inDir, "checkvariant_rules", this.variantRules);
        log.info("Subsystem {} v{} has {} roles, {} variant rules, {} namespace rules, and {} spreadsheet rows.",
                this.name, this.version, this.roles.size(), this.variantRules.size(), this.ruleMap.size(),
                this.spreadsheet.size());
    }

    /**
     * Now we read the subsystem spreadsheet, which contains the bulk of the data.  This includes the role list,
     * the auxiliary role set, and the genome rows.
     *
     * @param inDir		subsystem directory
     *
     * @throws IOException
     * @throws ParseFailureException
     */
    private void readSpreadsheet(File inDir) throws IOException, ParseFailureException {
        // The file has three sections, separated by "//" markers.
        try (LineReader reader = new LineReader(new File(inDir, "spreadsheet"))) {
            // Loop through the first section.  This has the roles.
            for (String[] line : reader.new Section(SECTION_MARKER)) {
                // The line contains an abbreviation and a role name.  We convert the role name to an ID
                // and create a rule for the abbreviation.
                String roleName = line[1];
                Role role = this.roleMap.getByName(roleName);
                if (role == null) {
                    // The role is not in the map.  This is only an error if the subsystem is a good one.
                    // If it is a bad one, we must add the role to the map.
                    String message = "Invalid role name \"" + roleName + "\" found in " + inDir + ".";
                    if (this.isGood())
                        throw new ParseFailureException(message);
                    else {
                        role = this.roleMap.findOrInsert(roleName);
                        log.warn(message);
                    }
                }
                this.ruleMap.put(line[0], new SubsystemPrimitiveRule(role.getId()));
                this.roles.add(role);
            }
            // The second section has the auxiliary roles.  These should be 1-based index numbers.
            this.auxRoles = new TreeSet<String>();
            for (String[] line : reader.new Section(SECTION_MARKER)) {
                if (line.length > 1 && line[0].toLowerCase().equals("aux")) {
                    // Here we have found the auxiliary roles definition.
                    // Loop through the role specifiers.
                    for (int i = 1; i < line.length; i++) {
                        String spec = line[i];
                        if (StringUtils.isNumeric(spec)) {
                            // Here we have a column index.
                            int idx = Integer.parseInt(spec);
                            if (idx <= 0 || idx > this.roles.size())
                                log.error("Invalid role index in aux role list.");
                            else
                                this.auxRoles.add(this.roles.get(idx - 1).getId());
                        } else
                            throw new ParseFailureException("Non-numeric auxiliary role spec \"" + spec + "\" in subsystem "
                                    + this.name + ".");
                    }
                }
            }
            // The final section has the spreadsheet rows themselves.
            for (String[] line : reader.new Section(SECTION_MARKER)) {
                Row newRow = this.new Row(line);
                this.spreadsheet.put(newRow.getGenomeId(), newRow);
            }
        }
        log.info("Spreadsheet for {} contained {} roles ({} auxiliary) and {} rows.", this.name,
                this.roles.size(), this.auxRoles.size(), this.spreadsheet.size());
    }

    /**
     * Read a file of variant rules or definitions, and store them in the specified map.
     *
     * @param inDir			subsystem directory
     * @param fileName		name of the source file
     * @param targetMap		rule map to receive the parsed rules
     *
     * @throws IOException
     * @throws ParseFailureException
     */
    private void readRules(File inDir, String fileName, Map<String, SubsystemRule> targetMap) throws IOException, ParseFailureException {
        // Compute the source file name.
        File inFile = new File(inDir, fileName);
        if (inFile.exists()) {
            log.info("Parsing rules in {}.", inFile);
            // Open the file for line-by-line reading.
            try (LineReader reader = new LineReader(inFile)) {
                // Loop through the rules.  Each is on a single input line.
                for (String line : reader) {
                    // Skip blank lines and comments.
                    if (! line.isBlank() && ! line.startsWith("#")) {
                        Matcher m = RULE_PATTERN.matcher(line);
                        if (! m.matches())
                            throw new ParseFailureException("Invalid rule line in \"" + inFile + "\".");
                        else {
                            // Now we parse the rule.  Note that the name space is the same regardless of the target
                            // map.
                            String key = m.group(1);
                            String rule = m.group(2);
                            SubsystemRule newRule = RuleCompiler.parseRule(rule, this.ruleMap);
                            targetMap.put(key, newRule);
                        }
                    }
                }
            }
        }
    }

    /**
     * Read in the classifications for the subsystem.
     *
     * @param inDir		subsystem input directory
     */
    private void setClassification(File inDir) {
        String classData = MarkerFile.readSafe(new File(inDir, "CLASSIFICATION"));
        this.classes = new ArrayList<String>(3);
        String[] pieces = StringUtils.splitPreserveAllTokens(classData, '\t');
        // Copy the classification pieces to the classification list.  There are always
        // supposed to be 3.  If we find an experimental class, we mark the subsystem as bad.
        this.good = true;
        for (int i = 0; i < pieces.length; i++) {
            String piece = pieces[i];
            if (piece.isBlank())
                this.classes.add("");
            else {
                this.classes.add(piece);
                if (piece.toLowerCase().startsWith("experimental") ||
                        piece.toLowerCase().startsWith("clustering"))
                    this.good = false;
            }
        }
        for (int i = pieces.length; i < 3; i++)
            this.classes.add("");
        if (this.good) {
            // If it's still good, check for exchangable.
            File exchangeMarker = new File(inDir, "EXCHANGABLE");
            if (! exchangeMarker.exists())
                this.good = false;
        }
        // Finally, get the version.
        File versionMarker = new File(inDir, "VERSION");
        this.version = MarkerFile.readInt(versionMarker);
    }

    /**
     * @return the name of the subsystem in the specified directory
     *
     * @param subDir	subsystem directory of interest
     */
    public static String dirToName(File subDir) {
        String name = subDir.getName();
        final int n = name.length();
        StringBuilder retVal = new StringBuilder(n);
        // Loop through the name, converting the translated characters.
        int i = 0;
        while (i < n) {
            char chr = name.charAt(i);
            switch (chr) {
            case '_' :
                // Underscores are encoded from spaces.
                retVal.append(' ');
                i++;
                break;
            case '%' :
                // Percent signs are used for hex encodings.
                String hex = name.substring(i + 1, i + 3);
                retVal.append((char) Integer.parseInt(hex, 16));
                i += 3;
                break;
            default :
                retVal.append(chr);
                i++;
            }
        }
        // Check for the pathological space trick.
        int last = retVal.length() - 1;
        while (last > 0 && retVal.charAt(last) == ' ') {
            retVal.setCharAt(last, '_');
            last--;
        }
        return retVal.toString();
    }
    /**
     * @return the name of the subsystem
     */
    public String getName() {
        return this.name;
    }
    /**
     * @return the subsystem version number
     */
    public int getVersion() {
        return this.version;
    }
    /**
     * @return TRUE if this is a good subsystem
     */
    public boolean isGood() {
        return this.good;
    }

    /**
     * @return the subsystem's superclass
     */
    public String getSuperClass() {
        return this.classes.get(0);
    }

    /**
     * @return the subsystem's class
     */
    public String getMiddleClass() {
        return this.classes.get(1);
    }

    /**
     * @return the subsystem's subclass
     */
    public String getSubClass() {
        return this.classes.get(2);
    }

    /**
     * @return a displayable classification string for this subsystem
     */
    public String getClassification() {
        String retVal = this.classes.stream().filter(x -> ! x.isBlank()).collect(Collectors.joining(", "));
        return retVal;
    }

    /**
     * @return TRUE if the specified role ID is an auxiliary role in this subsystem
     *
     * @param roleId	ID of the role of interest
     */
    public Object isAuxRole(String roleId) {
        return this.auxRoles.contains(roleId);
    }

    /**
     * @return the variant code of the specified genome in the spreadsheet, or NULL if it is not in the spreadsheet
     *
     * @param genomeId	ID of the genome of interest
     */
    public String variantOf(String genomeId) {
        String retVal = null;
        Row row = this.spreadsheet.get(genomeId);
        if (row != null)
            retVal = row.getVariantCode();
        return retVal;
    }

    /**
     * @return the set of feature IDs assigned to this subsystem for the specified genome row
     *
     * @param genomeId	ID of the genome of interest
     */
    public Set<String> fidSetOf(String genomeId) {
        Set<String> retVal = EMPTY_CELL;
        Row row = this.spreadsheet.get(genomeId);
        if (row != null)
            retVal = row.columns.stream().flatMap(x -> x.stream()).collect(Collectors.toSet());
        return retVal;
    }

    /**
     * Compute the variant code for a specified role set in this subsystem.
     *
     * @param roleSet	set of roles in the genome
     *
     * @return the optimal variant code, or NULL if no variant applies
     */
    public String applyRules(Set<String> roleSet) {
        // Get an iterator through the variant rules.  Because it is a linked hash map, we will get
        // them in order.
        Iterator<Map.Entry<String, SubsystemRule>> iter = this.variantRules.entrySet().iterator();
        String retVal = null;
        // Loop and stop on the first matching rule.
        while (iter.hasNext() && retVal == null) {
            var ruleEntry = iter.next();
            SubsystemRule rule = ruleEntry.getValue();
            if (rule.check(roleSet))
                retVal = ruleEntry.getKey();
        }
        return retVal;
    }

    /**
     * Create the role set for a genome.
     *
     * @param genome	genome of interest
     * @param roleMap	role definition structure
     *
     * @return a set of the role IDs for the genome's roles
     */
    public static Set<String> getRoleSet(Genome genome, RoleMap roleMap) {
        Set<String> retVal = new HashSet<String>(genome.getFeatureCount());
        for (Feature feat : genome.getFeatures())
            feat.getUsefulRoles(roleMap).stream().forEach(x -> retVal.add(x.getId()));
        return retVal;
    }


}
