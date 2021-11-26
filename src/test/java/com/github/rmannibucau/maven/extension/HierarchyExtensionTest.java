package com.github.rmannibucau.maven.extension;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HierarchyExtensionTest {
    @Test
    void rewritePom() throws PlexusContainerException {
        final var root = Projects.root();
        final var projects = List.of(
                root,
                Projects.child(root, "c1"),
                Projects.child(root, "c2")
        );
        final var session = new MavenSession(
                new DefaultPlexusContainer(new DefaultContainerConfiguration()),
                new Settings(),
                null, null, null, List.of(), null,
                new Properties(), new Properties(), new Date(0)) {
            @Override
            public List<MavenProject> getAllProjects() {
                return projects;
            }
        };

        new HierarchyExtension().afterProjectsRead(session);

        assertEquals(
                Map.of(
                        "test:c2:jar:0.0.1-SNAPSHOT", "" +
                                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                "<configuration>\n" +
                                "  <skip>false</skip>\n" +
                                "</configuration>",
                        "test:c1:jar:0.0.1-SNAPSHOT", "" +
                                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                "<configuration>\n" +
                                "  <skip>false</skip>\n" +
                                "</configuration>",
                        "test:root:pom:0.0.1-SNAPSHOT", "" +
                                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                "<configuration>\n" +
                                "  <skip>true</skip>\n" +
                                "</configuration>"),
                projects.stream()
                        .collect(toMap(MavenProject::getId, p -> p.getBuild().getPlugins().iterator().next().getConfiguration().toString()))
        );
    }

    private interface Projects {
        static MavenProject root() {
            final var project = new MavenProject();
            project.setGroupId("test");
            project.setArtifactId("root");
            project.setVersion("0.0.1-SNAPSHOT");
            project.setPackaging("pom");

            final var hierarchy = new Xpp3Dom("rmannibucau-hierarchy");
            hierarchy.setValue("parent(skip=true)|child(skip=false)");

            final var configuration = new Xpp3Dom("configuration");
            configuration.addChild(hierarchy);

            final var plugin = new Plugin();
            plugin.setGroupId("test.plugins");
            plugin.setArtifactId("p1");
            plugin.setVersion("0.0.1");
            plugin.setConfiguration(configuration);
            project.getBuild().getPlugins().add(plugin);

            return project;
        }

        static MavenProject child(final MavenProject parent, final String name) {
            final var project = new MavenProject();
            project.setParent(parent);
            project.setGroupId("test");
            project.setArtifactId(name);
            project.setVersion("0.0.1-SNAPSHOT");
            return project;
        }
    }
}
