package com.github.rmannibucau.maven.extension.fn;

import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public class SimpleRewrite implements Function<Xpp3Dom, Xpp3Dom> {
    private final Map<String, String> values;
    private final boolean copy;

    public SimpleRewrite(final Map<String, String> values, final boolean copy) {
        this.values = values;
        this.copy = copy;
    }

    @Override
    public Xpp3Dom apply(final Xpp3Dom xpp3Dom) {
        if (values == null || values.isEmpty()) {
            return xpp3Dom;
        }

        final var copy = this.copy ? copy(xpp3Dom) : xpp3Dom;
        values.forEach((key, value) -> {
            final var child = copy.getChild(key);
            if (child == null) {
                final var newChild = new Xpp3Dom(key);
                if (value != null) {
                    newChild.setValue(value);
                }
                copy.addChild(newChild);
            }
        });
        return copy;
    }

    private Xpp3Dom copy(final Xpp3Dom xpp3Dom) {
        final var dom = new Xpp3Dom(xpp3Dom.getName());
        dom.setValue(xpp3Dom.getValue());
        Stream.of(xpp3Dom.getAttributeNames())
                .forEach(attr -> dom.setAttribute(attr, xpp3Dom.getAttribute(attr)));
        Stream.of(xpp3Dom.getChildren())
                .forEach(child -> dom.addChild(copy(child)));
        return dom;
    }
}