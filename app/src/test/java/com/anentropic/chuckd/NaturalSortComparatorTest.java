package com.anentropic.chuckd;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChuckD.naturalCompare() — the natural sort comparator used
 * to order glob-expanded schema file paths.
 */
public class NaturalSortComparatorTest {

    // --- Basic numeric chunk ordering ---

    @Test
    void testNumericChunksOrderedByValue() {
        // "file1" < "file2" < "file10" (not lexicographic "file10" < "file2")
        assertTrue(ChuckD.naturalCompare("file1", "file2") < 0);
        assertTrue(ChuckD.naturalCompare("file2", "file10") < 0);
        assertTrue(ChuckD.naturalCompare("file1", "file10") < 0);
    }

    @Test
    void testVersionStringsOrderedCorrectly() {
        // "v8" < "v9" < "v10" (the motivating use case)
        assertTrue(ChuckD.naturalCompare("v8", "v9") < 0);
        assertTrue(ChuckD.naturalCompare("v9", "v10") < 0);
        assertTrue(ChuckD.naturalCompare("v8", "v10") < 0);
    }

    @Test
    void testPureNumericStrings() {
        // "1" < "2" < "10" (not "10" < "2")
        assertTrue(ChuckD.naturalCompare("1", "2") < 0);
        assertTrue(ChuckD.naturalCompare("2", "10") < 0);
        assertTrue(ChuckD.naturalCompare("8", "9") < 0);
        assertTrue(ChuckD.naturalCompare("9", "10") < 0);
    }

    // --- String-only (no numbers) ---

    @Test
    void testLexicographicWithNoNumbers() {
        assertTrue(ChuckD.naturalCompare("abc", "def") < 0);
        assertTrue(ChuckD.naturalCompare("def", "abc") > 0);
        assertTrue(ChuckD.naturalCompare("abc", "abd") < 0);
    }

    // --- Equal strings ---

    @Test
    void testEqualStringsReturnZero() {
        assertEquals(0, ChuckD.naturalCompare("abc", "abc"));
        assertEquals(0, ChuckD.naturalCompare("v10", "v10"));
        assertEquals(0, ChuckD.naturalCompare("file1.json", "file1.json"));
        assertEquals(0, ChuckD.naturalCompare("", ""));
    }

    // --- Mixed text and version numbers ---

    @Test
    void testSchemaVersionFileNames() {
        // "schema-v1.0.json" < "schema-v1.1.json" < "schema-v2.0.json"
        assertTrue(ChuckD.naturalCompare("schema-v1.0.json", "schema-v1.1.json") < 0);
        assertTrue(ChuckD.naturalCompare("schema-v1.1.json", "schema-v2.0.json") < 0);
        assertTrue(ChuckD.naturalCompare("schema-v1.0.json", "schema-v2.0.json") < 0);
    }

    @Test
    void testFilesWithJsonExtension() {
        // "file1.json" < "file2.json" < "file10.json"
        assertTrue(ChuckD.naturalCompare("file1.json", "file2.json") < 0);
        assertTrue(ChuckD.naturalCompare("file2.json", "file10.json") < 0);
        assertTrue(ChuckD.naturalCompare("file1.json", "file10.json") < 0);
    }

    // --- Sorting a list ---

    @Test
    void testSortListWithVersionNames() {
        List<String> names = Arrays.asList("v10", "v2", "v9", "v1", "v8");
        names.sort(ChuckD::naturalCompare);
        assertEquals(List.of("v1", "v2", "v8", "v9", "v10"), names);
    }

    @Test
    void testSortListWithFileNames() {
        List<String> names = Arrays.asList("file10.json", "file1.json", "file3.json", "file2.json");
        names.sort(ChuckD::naturalCompare);
        assertEquals(List.of("file1.json", "file2.json", "file3.json", "file10.json"), names);
    }

    // --- Large numeric chunks (Long range, not Integer) ---

    @Test
    void testLargeNumericChunks() {
        // Numbers larger than Integer.MAX_VALUE (2147483647)
        assertTrue(ChuckD.naturalCompare("schema-2147483648.json", "schema-2147483649.json") < 0);
        assertTrue(ChuckD.naturalCompare("schema-9999999999.json", "schema-10000000000.json") < 0);
    }
}
