package org.theseed.p3api;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonKey;
import com.github.cliftonlabs.json_simple.Jsoner;

/**
 * This object provides database information for the BV-BRC database. It is loaded from a JSON file,
 * which allows different users to have different maps. The mapping converts table and field names
 * from user-friendly equivalents to the internal database names, and specifies the sort order and
 * key fields for each table.
 */
public class BvbrcDataMap {

    // FIELDS
    /** logging facility */
    private static final Logger log = LoggerFactory.getLogger(BvbrcDataMap.class);
    /** map of user-friendly table names to table descriptors */
    private Map<String, Table> tableMap;
    /** empty mapping object */
    private static final JsonObject EMPTY_MAP = new JsonObject();
    /** default data map */
    public static final BvbrcDataMap DEFAULT_DATA_MAP = new BvbrcDataMap(Map.of(
        "genome", new Table("genome", "genome_id", "genome_id"),
        "genome_amr", new Table("genome_amr", "id", "id"),
        "feature", new Table("feature", "feature_id", "patric_id",
                "aa_sequence,sequence,aa_sequence_md5,sequence",
                "na_sequence,sequence,na_sequence_md5,sequence"),
        "taxon", new Table("taxonomy", "taxon_id", "taxon_id"),
        "contig", new Table("genome_sequence", "sequence_id", "sequence_id"),
        "sequence", new Table("feature_sequence", "md5", "md5"),
        "subsystem_item", new Table("subsystem", "id", "id"),
        "family", new Table("protein_family_ref", "family_id", "family_id"),
        "subsystem", new Table("subsystem_ref", "subsystem_name", "subsystem_name"),
        "special_gene", new Table("sp_gene", "id", "id")
    ));

    /**
     * This enum defines the keys used in the table objects.
     */
    private static enum DataKey implements JsonKey {
        /** table object keys */
        MAP(EMPTY_MAP),
        KEY("id"),
        SORT(""),
        NAME(""),
        /** derived-field keys */
        TABLE(""),
        SOURCE(""),
        VALUE("");

        /** default value */
        private final Object m_value;

        DataKey(final Object value) {
            this.m_value = value;
        }

        /** This is the string used as a key in the incoming JsonObject map.
         */
        @Override
        public String getKey() {
            return this.name().toLowerCase();
        }

        /** This is the default value used when the key is not found.
         */
        @Override
        public Object getValue() {
            return this.m_value;
        }

    }

    /**
     * This interface must be supported by a field descriptor.
     */

    public static interface IField { 

        /**
         * @rturn the internal name needed to process this field mapping.
         */
        public String getInternalName();
    }

    /**
     * This object specifies instructions for a derived field. A derived field exists in another record, and
     * to retrieve it, we need to know the user-friendly name of the target table, the field in the current
     * record that identifies the target record, and the internal name of the field in the target record
     * containing the desired value. So, in the feature table, the protein sequence is found in the
     * "sequence" table, it is identified by the "aa_sequence_md5" value in the feature record, and
     * retrieved from the "sequence" field.
     */
    public static class DerivedField implements IField{
        
        /** target table user-friendly name */
        private String targetTable;
        /** source field internal name */
        private String sourceField;
        /** target field user-friendly name */
        private String targetField;

        /**
         * Construct a derived field descriptor from a JSON object
         * 
         * @param json      JSON object containing the derived field description
         */
        public DerivedField(JsonObject json) {
            this.targetTable = json.getString(DataKey.TABLE);
            this.sourceField = json.getString(DataKey.SOURCE);
            this.targetField = json.getString(DataKey.VALUE);
        }

        /**
         * Construct a derived field descriptor from a table name, a source field name, and a target field
         * name.
         * 
         * @param targetTableName   user-friendly name of the target table
         * @param sourceFieldName   internal name of the source field
         * @param targetFieldName   user-friendly name of the target field
         */
        public DerivedField(String targetTableName, String sourceFieldName, String targetFieldName) {
            this.targetTable = targetTableName;
            this.sourceField = sourceFieldName;
            this.targetField = targetFieldName;
        }

        /**
         * @return the user-friendly name of the target table
         */
        public String getTargetTable() {
            return targetTable;
        }

        /**
         * @return the user-friendly name of the source field
         */
        @Override
        public String getInternalName() {
            return sourceField;
        }

        /**
         * @return the user-friendly name of the target field
         */
        public String getTargetField() {
            return targetField;
        }

    }

    /**
     * This is the most basic type of mapped field: one that has a user-friendly name.
     */
    public static class MappedField implements IField{

        /** internal field name */
        private String internalName;

        /**
         * Construct a mapped field descriptor.
         * 
         * @param name      internal name for the field
         */
        public MappedField(String name) {
            this.internalName = name;
        }

        /**
         * @return the internal name
         */
        @Override
        public String getInternalName() {
            return this.internalName;
        }

    }

    /**
     * This object describes the mapping data for a single table. Note that if
     * a field is not listed, its name is unchanged.
     */
    public static class Table {

        // FIELDS
        /** internal table name */
        private String name;
        /** map of user-friendly field names to internal field names */
        private Map<String, IField> fieldMap;
        /** internal name of key field */
        private String keyField;
        /** user-friendly name of key field */
        private String keyFieldName;
        /** internal name of sort field */
        private String sortField;

        /**
         * Construct a table descriptor for the specified table using a JSON object. Note that
         * the default internal name is the same as the user-friendly name and the default sort
         * field is the key field. The default key field is "id".
         * 
         * @param friendlyName  the user-friendly name of the table
         * @param json          the JSON object containing the table mapping information
         */
        public Table(String friendlyName, JsonObject json) {
            this.name = json.getString(DataKey.NAME);
            if (this.name.isEmpty())
                this.name = friendlyName;
            this.keyField = json.getString(DataKey.KEY);
            this.sortField = json.getString(DataKey.SORT);
            if (this.sortField.isEmpty())
                this.sortField = this.keyField;
            this.fieldMap = new TreeMap<String, IField>();
            // Default the user-friendly key name to the internal name.
            this.keyFieldName = this.keyField;
            // Now process the mapping. We save the user-friendly key name here.
            JsonObject jsonMap = json.getMap(DataKey.MAP);
            for (var mapEntry : jsonMap.entrySet()) {
                String fieldName = mapEntry.getKey();
                Object fieldValue = mapEntry.getValue();
                if (fieldValue instanceof String) {
                    this.fieldMap.put(fieldName, new MappedField((String) fieldValue));
                    if (this.keyField.equals((String) fieldValue))
                        this.keyFieldName = fieldName;
                } else if (fieldValue instanceof JsonObject) {
                    this.fieldMap.put(fieldName, new DerivedField((JsonObject) fieldValue));
                }
            }
        }

        /**
         * Construct a table descriptor with derived fields but no mappings.
         * 
         * @param internalName  the internal name of the table
         * @param sortField     the sort field for the table
         * @param keyField      the key field for the table
         * @param derivations   an array of derived-field strings, each containing a user-friendly
         *                      derived field name, the target table name, the source field name, 
         *                      and the target field name, comma-delimited
         * 
         * @return the specified table descriptor
         */
        public Table(String internalName, String sortField, String keyField, String... derivations) {
            this.name = internalName;
            this.fieldMap = new ConcurrentHashMap<>();
            this.sortField = sortField;
            this.keyField = keyField;
            for (String derivation : derivations) {
                String[] parts = StringUtils.split(derivation, ',');
                if (parts.length != 4)
                    throw new IllegalArgumentException("Derived field specification must have four parts: " + derivation);
                this.fieldMap.put(parts[0], new DerivedField(parts[1], parts[2], parts[3]));
            }
        }

        /**
         * @return the internal name of the table
         */
        public String getInternalName() {
            return this.name;
        }

        /**
         * @return the descriptor of a field, or NULL if the field is unmapped
         * 
         * @param friendlyName  the user-friendly name for the field
         */
        public IField getInternalFieldData(String friendlyName) {
            return this.fieldMap.getOrDefault(friendlyName, null);
        }

        /**
         * @return the internal name of a field, or NULL if it is a complex field
         */
        public String getInternalFieldName(String friendlyName) {
            String retVal = null;
            IField fieldData = this.fieldMap.get(friendlyName);
            if (fieldData instanceof MappedField)
                retVal = ((MappedField) fieldData).getInternalName();
            else if (fieldData == null)
                retVal = friendlyName;
            return retVal;
        }

        /**
         * @return the internal name of the sort field
         */
        public String getInternalSortField() {
            return this.sortField;
        }

        /**
         * @return the internal name of the key field
         */
        public String getInternalKeyField() {
            return this.keyField;
        }

        /**
         * @return the external name of the key field
         */
        public String getKeyField() {
            return this.keyFieldName;
        }

        public String getUserFieldName(String linkField) {
            // The default user field name is the internal name. This will be our output
            // most of the time.
            String retVal = linkField;
            // Check for a mapping in the table map that leads to the link field.
            Iterator<Map.Entry<String, IField>> iter = this.fieldMap.entrySet().iterator();
            boolean done = ! (this.fieldMap.isEmpty());
            while (iter.hasNext() && !done) {
                Map.Entry<String, IField> entry = iter.next();
                IField field = entry.getValue();
                if (field instanceof MappedField && field.getInternalName().equals(linkField)) {
                    retVal = entry.getKey();
                    done = true;
                }
            }
            return retVal;
        }

    }

    /**
     * Construct a blank, empty BV-BRC data map.
     */
    public BvbrcDataMap() {
        this.tableMap = new HashMap<>();
    }

    /**
     * Construct a BV-BRC data map from a map of table descriptors.
     * 
     * @param tables  a map of friendly tab le names to table descriptors
     * 
     * @return the constructed BV-BRC data map
     */
    public BvbrcDataMap(Map<String, BvbrcDataMap.Table> tables) {
        this();
        for (var tableEntry : tables.entrySet()) {
            this.tableMap.put(tableEntry.getKey(), tableEntry.getValue());
        }
    }

    /**
     * Load a table map from a JSON file.
     * 
     * @param file  name of the JSON file containing the table mapping information
     * 
     * @return the table map loaded
     * 
     * @throws IOException
     * @throws JsonException 
     */
    public static BvbrcDataMap load(File file) throws IOException, JsonException {
        log.info("Loading BV-BRC data map from {}.", file);
        BvbrcDataMap retVal = new BvbrcDataMap();
        try (FileReader reader = new FileReader(file)) {
            JsonObject json = (JsonObject) Jsoner.deserialize(reader);
            for (var tableEntry : json.entrySet()) {
                String tableName = tableEntry.getKey();
                JsonObject tableJson = (JsonObject) tableEntry.getValue();
                Table table = new Table(tableName, tableJson);
                retVal.tableMap.put(tableName, table);
            }
        }
        return retVal;
    }

    /**
     * Get the table descriptor for a specified table. If the table does not
     * exist in the map, this method throws an error.
     * 
     * @param tableName  the user-friendly name of the table
     * 
     * @return the table descriptor for a specified table
     * 
     * @throws IOException
     */
    public Table getTable(String tableName) throws IOException {
        Table retVal = this.tableMap.get(tableName);
        if (retVal == null)
            throw new IOException("Table not found: " + tableName);
        return retVal;
    }

    /**
     * @return the number of tables in the data map
     */
    public int size() {
        return this.tableMap.size();
    }
    
}
