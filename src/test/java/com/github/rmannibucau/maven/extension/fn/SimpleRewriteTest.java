package com.github.rmannibucau.maven.extension.fn;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class SimpleRewriteTest {
    @Test
    void rewrite() {
        final var in = new Xpp3Dom("configuration");
        final var skip = new Xpp3Dom("skip");
        skip.setValue("true");
        in.addChild(skip);
        final var other = new Xpp3Dom("other");
        other.setValue("ok");
        in.addChild(other);

        final var result = new SimpleRewrite(Map.of(
                "skipTests", "true",
                // ignored cause already explicitly set
                "skip", "false"), true).apply(in);
        assertNotSame(in, result);

        assertEquals("" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<configuration>\n" +
                "  <skip>true</skip>\n" +
                "  <other>ok</other>\n" +
                "  <skipTests>true</skipTests>\n" +
                "</configuration>", result.toString());
    }
}
