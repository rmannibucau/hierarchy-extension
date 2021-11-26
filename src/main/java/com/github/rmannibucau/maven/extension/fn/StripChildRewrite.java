package com.github.rmannibucau.maven.extension.fn;

import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.function.Function;

public class StripChildRewrite implements Function<Xpp3Dom, Xpp3Dom> {
    private final String tag;

    public StripChildRewrite(final String tag) {
        this.tag = tag;
    }

    @Override
    public Xpp3Dom apply(final Xpp3Dom xpp3Dom) {
        for (int i = 0; i < xpp3Dom.getChildCount(); i++) {
            final var child = xpp3Dom.getChild(i);
            if (!tag.equals(child.getName())) {
                continue;
            }
            xpp3Dom.removeChild(i);
            break;
        }
        return xpp3Dom;
    }
}