package org.theseed.p3api;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * This enum defines the keys used in the table objects.
     */
    private static enum DataKey implements JsonKey {
        MAP(EMPTY_MAP),
        KEY("id"),
        SORT(""),
        NAME("");


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
     * This object describes the mapping data for a single table. Note that if
     * a field is not listed, its name is unchanged.
     */
    public static class Table {

        // FIELDS
        /** user-friendly table name */
        private String name;
        /** map of user-friendly field names to internal field names */
        private Map<String, String> fieldMap;
        /** key field */
        private String keyField;
        /** sort field */
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
            this.fieldMap = json.getMap(DataKey.MAP);
            this.keyField = json.getString(DataKey.KEY);
            this.sortField = json.getString(DataKey.SORT);
            if (this.sortField.isEmpty())
                this.sortField = this.keyField;
        }

        /**
         * @return the internal name of the table
         */
        public String getInternalName() {
            return this.name;
        }

        /**
         * @return the internal name of a field
         * 
         * @param friendlyName  the user-friendly name for the field
         */
        public String getInternalFieldName(String friendlyName) {
            return this.fieldMap.getOrDefault(friendlyName, friendlyName);
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

    }

    /**
     * Construct a blank, empty BV-BRC data map.
     */
    public BvbrcDataMap() {
        this.tableMap = new HashMap<>();
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
