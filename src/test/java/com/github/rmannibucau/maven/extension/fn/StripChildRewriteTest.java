package com.github.rmannibucau.maven.extension.fn;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StripChildRewriteTest {
    @Test
    void noop() {
        final var in = new Xpp3Dom("dom");
        assertEquals(in, new StripChildRewrite("foo").apply(in));
        assertEquals(0, in.getChildCount());

        final var bar = new Xpp3Dom("bar");
        in.addChild(bar);
        bar.setValue("dummy");
        assertEquals(in, new StripChildRewrite("foo").apply(in));
        assertEquals(1, in.getChildCount());
        final var postChild = in.getChild(0);
        assertEquals("bar", postChild.getName());
        assertEquals("dummy", postChild.getValue());
    }

    @Test
    void strip() {
        final var in = new Xpp3Dom("dom");
        in.addChild(new Xpp3Dom("foo"));
        in.addChild(new Xpp3Dom("bar"));
        assertEquals(in, new StripChildRewrite("foo").apply(in));
        assertEquals(1, in.getChildCount());
        assertEquals("bar", in.getChild(0).getName());
    }
}
