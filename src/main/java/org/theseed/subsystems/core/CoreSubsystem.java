/**
 *
 */
package org.theseed.subsystems.core;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
    /** original role names, in order */
    private List<String> roleNames;
    /** map of role IDs to original names */
    private Map<String, String> roleIdNameMap;
    /** subsystem spreadsheet */
    private Map<String, Row> spreadsheet;
    /** classifications */
    private List<String> classes;
    /** set of invalid identifiers */
    private Set<String> badIds;
    /** number of invalid roles */
    private int badRoles;
    /** roles not found during current rule */
    private Set<String> notFound;
    /** roles found during current rule */
    private Set<String> found;
    /** list of feature types used in subsystems */
    public static final String[] FID_TYPES = new String[] { "opr", "aSDomain", "pbs", "rna", "rsw", "sRNA", "peg" };
    /** common representation of an empty cell */
    private static final Set<String> EMPTY_CELL = Collections.emptySet();
    /** marker to separate file sections */
    private static final String SECTION_MARKER = "//";
    /** main rule parser, for separating the rule name and the text to compile */
    private static final Pattern RULE_PATTERN = Pattern.compile("\\s*(\\S+)\\s+(?:(?:means|if|is)\\s+)?(.+)");
    /** subsystem directory filter */
    private static final FileFilter DIR_SS_FILTER = new FileFilter() {

        @Override
        public boolean accept(File pathname) {
            // We accept the file if it is a directory and has a spreadsheet file in it.
            boolean retVal = pathname.isDirectory();
            if (retVal) {
                File ssFile = new File(pathname, "spreadsheet");
                retVal = ssFile.exists();
            }
            return retVal;
        }

    };

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
        // Clear the error counters.
        this.badRoles = 0;
        this.badIds = new TreeSet<String>();
        // Compute the real subsystem name.
        this.name = dirToName(inDir);
        log.info("Reading subsystem {}.", this.name);
        // Save the role definitions.
        this.roleMap = new RoleMap();
        this.roleIdNameMap = new HashMap<String, String>();
        // Now get the classification and version.
        this.setClassification(inDir);
        // Initialize the rule namespace and the role list.
        this.ruleMap = new HashMap<String, SubsystemRule>();
        this.roles = new ArrayList<Role>();
        this.roleNames = new ArrayList<String>();
        // Initialize the tracking sets.
        this.found = new TreeSet<String>();
        this.notFound = new TreeSet<String>();
        // Read in the subsystem spreadsheet.  This will initialize the name space, collect the
        // rule list and auxiliary rules, and store the rows.
        this.spreadsheet = new HashMap<String, Row>();
        this.readSpreadsheet(roleDefs, inDir);
        // Compile the variant rules.
        this.variantRules = new LinkedHashMap<String, SubsystemRule>();
        this.readRules(inDir, "checkvariant_definitions", this.ruleMap);
        this.readRules(inDir, "checkvariant_rules", this.variantRules);
        log.info("Subsystem {} v{} has {} roles, {} variant rules, {} namespace rules, and {} spreadsheet rows.",
                this.name, this.version, this.roles.size(), this.variantRules.size(), this.ruleMap.size(),
                this.spreadsheet.size());
    }

    /**
     * This is a dummy core-subsystem to use as a placeholder.
     */
    public CoreSubsystem() {
        this.name = "(none)";
        this.good = false;
        this.auxRoles = Collections.emptySet();
        this.badIds = Collections.emptySet();
        this.classes = Arrays.asList("", "", "");
        this.found = Collections.emptySet();
        this.notFound = Collections.emptySet();
        this.roleMap = new RoleMap();
        this.roleIdNameMap = new TreeMap<String, String>();
        this.roles = Collections.emptyList();
        this.ruleMap = Collections.emptyMap();
        this.variantRules = new LinkedHashMap<String, SubsystemRule>();
        this.spreadsheet = Collections.emptyMap();
        this.version = 1;
    }

    /**
     * Now we read the subsystem spreadsheet, which contains the bulk of the data.  This includes the role list,
     * the auxiliary role set, and the genome rows.
     *
     * @param roleDefs	role definition map
     * @param inDir		subsystem directory
     *
     * @throws IOException
     * @throws ParseFailureException
     */
    private void readSpreadsheet(RoleMap roleDefs, File inDir) throws IOException, ParseFailureException {
        // The file has three sections, separated by "//" markers.
        File inFile = new File(inDir, "spreadsheet");
        try (LineReader reader = new LineReader(inFile)) {
            // Loop through the first section.  This has the roles.
            int ruleIdx = 1;
            for (String[] line : reader.new Section(SECTION_MARKER)) {
                // The line contains an abbreviation and a role name.  We convert the role name to an ID
                // and create a rule for the abbreviation.
                String roleName = line[1];
                Role role = roleDefs.getByName(roleName);
                if (role == null) {
                    // The role is not in the map, which is a role error.
                    log.warn("Invalid role name \"{}\" found in {}.", roleName, inFile);
                    this.badRoles++;
                    role = new Role("invalid", roleName);
                }
                // Create the rule for this role.
                var roleRule = new SubsystemPrimitiveRule(role.getId());
                // Connect it to the abbreviation.
                this.ruleMap.put(line[0], roleRule);
                roleRule.setTracking(this, line[0]);
                // Establish the role in the current column position.
                this.ruleMap.put(Integer.toString(ruleIdx), roleRule);
                this.roles.add(role);
                this.roleNames.add(roleName);
                this.roleIdNameMap.put(role.getId(), roleName);
                ruleIdx++;
                // Add the role to the internal role map.
                var roles = roleDefs.getAllById(role.getId());
                this.roleMap.putAll(roles);
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
                            RuleCompiler compiler = new RuleCompiler(rule, this.ruleMap);
                            SubsystemRule newRule = compiler.compiledRule();
                            targetMap.put(key, newRule);
                            this.badIds.addAll(compiler.getBadIds());
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
     * @return the number of genome rows in the spreadsheet
     */
    public int getRowCount() {
        return this.spreadsheet.size();
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
        // Clear the tracking sets.  These are only available after an analyzeRule.
        this.found.clear();
        this.notFound.clear();
        // Return the results.
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

    /**
     * @return the list of subsystem directories for a CoreSEED instance
     *
     * @param coreDir	CoreSEED data directory name
     *
     * @throws IOException
     */
    public static List<File> getSubsystemDirectories(File coreDir) throws IOException {
        File subMaster = new File(coreDir, "Subsystems");
        if (! subMaster.isDirectory())
            throw new FileNotFoundException(coreDir + " does not appear to be a CoreSEED data directory.");
        File[] subFiles = subMaster.listFiles(DIR_SS_FILTER);
        return Arrays.asList(subFiles);
    }

    /**
     * @return the number of bad rule identifiers
     */
    public int getBadIdCount() {
        return this.badIds.size();
    }

    /**
     * @return the set of bad rule identifiers
     */
    public Set<String> getBadIds() {
        return this.badIds;
    }

    /**
     * @return the number of bad role names
     */
    public int getBadRoleCount() {
        return this.badRoles;
    }

    /**
     * @return the set of genomes in the spreadsheet
     */
    public Set<String> getRowGenomes() {
        return this.spreadsheet.keySet();
    }

    /**
     * @return the number of roles in this subsystem
     */
    public int getRoleCount() {
        return this.roles.size();
    }

    /**
     * @return TRUE if there are variant rules for this subsystem
     */
    public boolean hasRules() {
        return this.variantRules.size() > 0;
    }

    /**
     * @return TRUE if the specified variant code has a rule
     *
     * @param vCode		variant code to check
     */
    public boolean isRuleVariant(String vCode) {
        return this.variantRules.containsKey(vCode);
    }

    /**
     * Record the results of a primitive-rule match.
     *
     * @param abbr		role abbreviation
     * @param retVal	TRUE if the role was found, else FALSE
     */
    public void record(String abbr, boolean retVal) {
        if (retVal)
            this.found.add(abbr);
        else
            this.notFound.add(abbr);
    }

    /**
     * Analyze the specified rule to determine what was found and not found.
     *
     * @param ruleName		name of the rule to analyze
     * @param roleSet		set of roles to use
     *
     * @return a string containing the abbreviations of the found roles, a slash, and the abbreviations of the missing roles
     *
     * @throws ParseFailureException
     */
    public String analyzeRule(String ruleName, Set<String> roleSet) throws ParseFailureException {
        String retVal;
        // Insure the tracking sets are empty.
        this.notFound.clear();
        this.found.clear();
        // Test the rule.
        SubsystemRule rule = this.variantRules.get(ruleName);
        if (rule == null)
            retVal = "<no match>";
        else {
            rule.check(roleSet);
            retVal = StringUtils.join(this.found, ",") + "/" + StringUtils.join(this.notFound, ",");
        }
        return retVal;
    }

    /**
     * @return the rule with the given name, or NULL if there is none
     *
     * @param name 		name of the desired rule
     */
    public SubsystemRule getRule(String name) {
        SubsystemRule retVal = this.variantRules.get(name);
        if (retVal == null)
            retVal = this.ruleMap.get(name);
        return retVal;
    }

    /**
     * This can be used immediately after an "analyzeRule" to get the roles not found.
     *
     * @return the roles not-found
     */
    public Set<String> getNotFound() {
        return this.notFound;
    }

    /**
     * This can be used immediately after an "analyzeRule" to get the roles found.
     *
     * @return the roles found
     */
    public Set<String> getFound() {
        return this.found;
    }

    /**
     * @return the main rule name space for this subsystem
     */
    protected Map<String, SubsystemRule> getNameSpace() {
        return this.ruleMap;
    }

    /**
     * @return a map from role IDs to original names for this subsystem
     */
    public Map<String, String> getOriginalNameMap() {
        final int n = this.roles.size();
        Map<String, String> retVal = new HashMap<String, String>(n * 4 / 3 + 1);
        for (int i = 0; i < n; i++) {
            String roleId = this.roles.get(i).getId();
            String roleName = this.roleNames.get(i);
            retVal.put(roleId, roleName);
        }
        return retVal;
    }

    /**
     * This method will look at a role and return TRUE if it exactly matches one of the subsystem roles, else FALSE.
     *
     * @param roleId		ID of the role to check
     * @param roleString	role string to check
     *
     * @return TRUE if the role string exactly matches a subsystem role, else FALSE
     */
    public boolean isExactRole(String roleId, String roleString) {
        String expectedString = this.roleIdNameMap.get(roleId);
        return StringUtils.equals(roleString, expectedString);
    }

    /**
     * This method will look at a role string and return the role ID if the role is in this subsystem, else NULL.
     *
     * @param roleString	role string to check
     *
     * @return the ID of the role if it is in the subsystem, else NULL
     */
    public String getRoleId(String roleString) {
        String retVal = null;
        Role role = this.roleMap.getByName(roleString);
        if (role != null)
            retVal = role.getId();
        return retVal;
    }

    /**
     * @return the expected role string for the role with the specified ID
     *
     * @param roleId	ID of the desired role
     *
     * @return the actual name of the role in this subsystem, or NULL if the role ID is not found
     */
    public String getExpectedRole(String roleId) {
        String retVal = null;
        // Find the role ID in the role list.
        int i = 0;
        final int n = this.roles.size();
        while (i < n && ! this.roles.get(i).getId().contentEquals(roleId))
            i++;
        if (i < n)
            retVal = this.roleNames.get(i);
        return retVal;
    }

}
