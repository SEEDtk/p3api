/**
 *
 */
package org.theseed.p3api;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonKey;
import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This is a simple class for creating Json keys on the fly.
 *
 * @author Bruce Parrello
 *
 */
public class KeyBuffer implements JsonKey {

	// FIELDS
	/** name of the key */
    private String keyName;
    /** value to use when the key is not present */
    private Object defaultValue;
	/** empty string list */
	public static final JsonArray EMPTY_LIST = new JsonArray();

    public KeyBuffer(String name, Object def) {
        this.keyName = name;
        this.defaultValue = def;
    }

    @Override
    public String getKey() {
        return this.keyName;
    }

    @Override
    public Object getValue() {
        return this.defaultValue;
    }

    /**
     * Use this key object with the specified name.
     *
     * @param newName	new name to assign to the key
     *
     * @return the key object for use as a parameter to a typed get
     */
    public KeyBuffer use(String newName) {
        this.keyName = newName;
        return this;
    }

	/**
	 * Extract a list of integers from a record field.
	 *
	 * @param record	record containing the field
	 * @param keyName		name of the field containing the numbers
	 *
	 * @return an array of the integers in the field
	 */
	public static int[] getIntegerList(JsonObject record, String keyName) {
	    KeyBuffer listBuffer = new KeyBuffer(keyName, EMPTY_LIST);
	    JsonArray list = record.getCollectionOrDefault(listBuffer);
	    int length = list.size();
	    int[] retVal = new int[length];
	    for (int i = 0; i < length; i++) {
	        retVal[i] = list.getInteger(i);
	    }
	    return retVal;
	}

	/**
	 * Extract a list of strings from a record field.
	 *
	 * @param record	source record
	 * @param keyName	name of the field containing the strings
	 *
	 * @return an array of the strings in the field
	 */
	public static String[] getStringList(JsonObject record, String keyName) {
	    KeyBuffer listBuffer = new KeyBuffer(keyName, EMPTY_LIST);
	    JsonArray list = record.getCollectionOrDefault(listBuffer);
	    int length = list.size();
	    String[] retVal = new String[length];
	    for (int i = 0; i < length; i++) {
	        retVal[i] = list.getString(i);
	    }
	    return retVal;
	}

	/**
	 * Extract a floating-point number from a record field.
	 *
	 * @param record	source record
	 * @param keyName	name of the field containing a floating-point number
	 *
	 * @return the floating-point value of the field
	 */
	public static double getDouble(JsonObject record, String keyName) {
	    KeyBuffer doubleBuffer = new KeyBuffer(keyName, 0.0);
	    double retVal = record.getDoubleOrDefault(doubleBuffer);
	    return retVal;
	}

	/**
	 * Extract an integer from a record field.
	 *
	 * @param record	source record
	 * @param keyName	name of the field containing an integer number
	 *
	 * @return the integer value of the field
	 */
	public static int getInt(JsonObject record, String keyName) {
	    KeyBuffer intBuffer = new KeyBuffer(keyName, 0);
	    int retVal = record.getIntegerOrDefault(intBuffer);
	    return retVal;
	}

	/**
	 * Extract a string from a record field.
	 *
	 * @param record	source record
	 * @param keyName	name of the field containing a string
	 *
	 * @return the string value of the field
	 */
	public static String getString(JsonObject record, String keyName) {
	    KeyBuffer stringBuffer = new KeyBuffer(keyName, "");
	    String retVal = record.getStringOrDefault(stringBuffer);
	    return retVal;
	}

}
