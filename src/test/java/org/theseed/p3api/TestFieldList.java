package org.theseed.p3api;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.jupiter.api.Test;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;

public class TestFieldList {

    @Test
    public void testFieldList() throws IOException, JsonException {
        File testFile = new File("data", "patric.json");
        BvbrcDataMap dataMap = BvbrcDataMap.load(testFile);
        assertThat(dataMap, is(notNullValue()));
        CursorConnection p3 = new CursorConnection(dataMap);
        assertThat(p3, is(notNullValue()));
        JsonArray result = p3.getFieldList("genome");
        assertThat(result.size(), greaterThan(100));
        int found = 0;
        for (Object fieldObject : result) {
            assertThat(fieldObject, instanceOf(JsonObject.class));
            JsonObject field = (JsonObject) fieldObject;
            String name = KeyBuffer.getString(field, "name");
            assertThat(name, not(emptyString()));
            String type = KeyBuffer.getString(field, "type");
            assertThat(type, not(emptyString()));
            switch (name) {
            case "genome_id" -> {
                assertThat(type, equalTo("string"));
                assertThat((Boolean) KeyBuffer.getFlag(field, "indexed"), equalTo(true));
                assertThat((Boolean) KeyBuffer.getFlag(field, "multiValued"), equalTo(false));
                found++;
                }
            case "genome_name" -> {
                assertThat(type, equalTo("string_ci"));
                assertThat((Boolean) KeyBuffer.getFlag(field, "indexed"), equalTo(true));
                assertThat((Boolean) KeyBuffer.getFlag(field, "multiValued"), equalTo(false));
                found++;
                }
            case "taxon_id" -> {
                assertThat(type, equalTo("int"));
                assertThat((Boolean) KeyBuffer.getFlag(field, "indexed"), equalTo(true));
                assertThat((Boolean) KeyBuffer.getFlag(field, "multiValued"), equalTo(false));
                found++;
                }
            case "taxon_lineage_names" -> {
                assertThat(type, equalTo("string"));
                assertThat((Boolean) KeyBuffer.getFlag(field, "indexed"), equalTo(true));
                assertThat((Boolean) KeyBuffer.getFlag(field, "multiValued"), equalTo(true));
                found++;
                }
            }
        }
        assertThat(found, equalTo(4));
    }
}
