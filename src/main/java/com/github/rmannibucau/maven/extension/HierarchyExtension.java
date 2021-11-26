package com.github.rmannibucau.maven.extension;

import com.github.rmannibucau.maven.extension.fn.SimpleRewrite;
import com.github.rmannibucau.maven.extension.fn.StripChildRewrite;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "rmannibucau-hierarchy-participant")
public class HierarchyExtension extends AbstractMavenLifecycleParticipant {
    private final Logger logger = LoggerFactory.getLogger(getClass().getName());
    private final String attributeName = "rmannibucau-hierarchy";

    @Override
    public void afterProjectsRead(final MavenSession session) {
        rewire(session.getAllProjects());
    }

    private void rewire(final List<MavenProject> all) {
        final var pom = all.stream()
                .filter(it -> "pom".equals(it.getPackaging()))
                .collect(toList());
        pom.forEach(it -> rewire(it, all));
    }

    // todo: instead of just supporting true, support prepend/append position
    private void rewire(final MavenProject pom, final List<MavenProject> all) {
        final var allPlugins = pom.getBuild().getPlugins();
        final var children = all.stream()
                .filter(p -> p.hasParent() && p.getParent() == pom)
                .collect(toMap(
                        p -> p,
                        p -> p.getBuild().getPlugins().stream().collect(toMap(Plugin::getKey, identity())),
                        (a, b) -> a,
                        LinkedHashMap::new));
        if (children.isEmpty()) {
            return;
        }

        allPlugins
                .stream()
                .filter(it -> Xpp3Dom.class.isInstance(it.getConfiguration()))
                .forEach(it -> {
                    rewriteRootConfiguration(children, pom, it);
                    rewriteExecutionsConfiguration(children, pom, it);
                });
    }

    private void rewriteExecutionsConfiguration(final Map<MavenProject, Map<String, Plugin>> children,
                                                final MavenProject pom,
                                                final Plugin plugin) {
        plugin.getExecutions().stream()
                .filter(it -> Xpp3Dom.class.isInstance(it.getConfiguration()))
                .forEach(execution -> {
                    final var xpp3 = Xpp3Dom.class.cast(execution.getConfiguration());
                    final var overrides = getOverrides(xpp3);
                    if (overrides == null) {
                        return;
                    }

                    final var stringOverrides = overrides.getValue();
                    if (stringOverrides != null) {
                        final var parentRewrite = extractAttributes("parent", stringOverrides);
                        if (parentRewrite != null) {
                            logger.debug("Rewriting configuration for " +
                                    plugin + "#" + execution.getId() + " in " + pom.getId() +
                                    " with attributes: " + parentRewrite.keySet());
                            execution.setConfiguration(new SimpleRewrite(parentRewrite, false)
                                    .andThen(new StripChildRewrite(attributeName))
                                    .apply(xpp3));
                        }

                        final var childrenRewrite = extractAttributes("child", stringOverrides);
                        if (childrenRewrite != null) {
                            children.forEach((project, plugins) -> {
                                var childDefinition = plugins.get(plugin.getKey());
                                if (childDefinition == null) { // create it all
                                    childDefinition = new Plugin();
                                    childDefinition.setGroupId(plugin.getGroupId());
                                    childDefinition.setArtifactId(plugin.getArtifactId());
                                    childDefinition.setVersion(plugin.getVersion());
                                    logger.debug("Adding plugin in " + project.getId());
                                    project.getBuild().getPlugins().add(childDefinition);
                                }

                                final var childPlugin = childDefinition;
                                childDefinition.getExecutions().stream()
                                        .filter(it -> {
                                            final var sameId = Objects.equals(execution.getId(), it.getId());
                                            return (sameId && execution.getId() != null) ||
                                                    /*should be an intersection but sufficient for now*/
                                                    (sameId && Objects.equals(execution.getGoals(), it.getGoals()));
                                        })
                                        .findFirst()
                                        .map(existingExec -> {
                                            logger.debug("Rewriting configuration for " +
                                                    plugin + "#" + execution.getId() + " in " + project.getId() +
                                                    " with attributes: " + childrenRewrite.keySet());
                                            existingExec.setConfiguration(new SimpleRewrite(childrenRewrite, false)
                                                    .andThen(new StripChildRewrite(attributeName))
                                                    .apply(Xpp3Dom.class.cast(existingExec.getConfiguration())));
                                            return true;
                                        })
                                        .orElseGet(() -> { // create it
                                            final var exec = new PluginExecution();
                                            exec.setId(execution.getId());
                                            exec.setGoals(execution.getGoals());
                                            exec.setPhase(execution.getPhase());
                                            exec.setConfiguration(new SimpleRewrite(childrenRewrite, true).apply(new Xpp3Dom("configuration")));
                                            logger.debug("Adding execution " + exec.getId() + "configuration for " +
                                                    plugin + "#" + execution.getId() + " in " + project.getId() +
                                                    " with attributes: " + childrenRewrite.keySet());
                                            childPlugin.getExecutions().add(exec);
                                            return false;
                                        });
                            });
                        }
                    } else {
                        final var parentOverride = overrides.getChild("parent");
                        if (parentOverride != null) {
                            logger.debug("Rewriting configuration for " +
                                    plugin + "#" + execution.getId() + " in " + pom.getId() +
                                    " with: " + parentOverride);
                            execution.setConfiguration(Xpp3Dom.mergeXpp3Dom(xpp3, parentOverride));
                        }

                        final var childrenOverride = overrides.getChild("child");
                        if (childrenOverride != null) {
                            children.forEach((project, plugins) -> {
                                var childDefinition = plugins.get(plugin.getKey());
                                if (childDefinition == null) { // create it all
                                    childDefinition = new Plugin();
                                    childDefinition.setGroupId(plugin.getGroupId());
                                    childDefinition.setArtifactId(plugin.getArtifactId());
                                    childDefinition.setVersion(plugin.getVersion());
                                    logger.debug("Adding plugin in " + project.getId());
                                    project.getBuild().getPlugins().add(childDefinition);
                                }
                                final var childPlugin = childDefinition;
                                childDefinition.getExecutions().stream()
                                        .filter(it -> {
                                            final var sameId = Objects.equals(execution.getId(), it.getId());
                                            return (sameId && execution.getId() != null) ||
                                                    /*should be an intersection but sufficient for now*/
                                                    (sameId && Objects.equals(execution.getGoals(), it.getGoals()));
                                        })
                                        .findFirst()
                                        .map(existingExec -> {
                                            logger.debug("Rewriting configuration for " +
                                                    plugin + "#" + execution.getId() + " in " + project.getId() +
                                                    " with: " + childrenOverride);
                                            existingExec.setConfiguration(new StripChildRewrite(attributeName)
                                                    .apply(Xpp3Dom.mergeXpp3Dom(Xpp3Dom.class.cast(existingExec.getConfiguration()), childrenOverride)));
                                            return true;
                                        })
                                        .orElseGet(() -> { // create it
                                            final var exec = new PluginExecution();
                                            exec.setId(execution.getId());
                                            exec.setGoals(execution.getGoals());
                                            exec.setPhase(execution.getPhase());
                                            exec.setConfiguration(childrenOverride);
                                            logger.debug("Adding execution " + exec.getId() + "configuration for " +
                                                    plugin + "#" + execution.getId() + " in " + project.getId() +
                                                    " with: " + childrenOverride);
                                            childPlugin.getExecutions().add(exec);
                                            return false;
                                        });
                            });
                        }
                    }
                });
    }

    private void rewriteRootConfiguration(final Map<MavenProject, Map<String, Plugin>> children,
                                          final MavenProject pom,
                                          final Plugin plugin) {
        final var xpp3 = Xpp3Dom.class.cast(plugin.getConfiguration());
        final var overrides = getOverrides(xpp3);
        if (overrides == null) {
            return;
        }

        final var stringOverrides = overrides.getValue();

        if (stringOverrides != null) {
            final var parentRewrite = extractAttributes("parent", stringOverrides);
            if (parentRewrite != null) {
                logger.debug("Rewriting " + plugin + " configuration in " + pom.getId() + " for attributes: " + parentRewrite.keySet());
                plugin.setConfiguration(new SimpleRewrite(parentRewrite, false)
                        .andThen(new StripChildRewrite(attributeName))
                        .apply(xpp3));
            }

            final var childrenRewrite = extractAttributes("child", stringOverrides);
            if (childrenRewrite != null) {
                children.forEach((project, plugins) -> {
                    final var childDefinition = plugins.get(plugin.getKey());
                    if (Xpp3Dom.class.isInstance(childDefinition.getConfiguration())) { // just rewrite since it exists
                        logger.debug("Rewriting configuration for " + plugin + " in " + project.getId() + " with attributes: " + childrenRewrite.keySet());
                        childDefinition.setConfiguration(
                                new SimpleRewrite(childrenRewrite, false)
                                        .andThen(new StripChildRewrite(attributeName))
                                        .apply(Xpp3Dom.class.cast(childDefinition.getConfiguration())));
                    } else {
                        final var newPlugin = new Plugin();
                        newPlugin.setGroupId(plugin.getGroupId());
                        newPlugin.setArtifactId(plugin.getArtifactId());
                        newPlugin.setVersion(plugin.getVersion());
                        newPlugin.setConfiguration(new SimpleRewrite(childrenRewrite, true).apply(new Xpp3Dom("configuration")));
                        logger.debug("Adding " + plugin + " configuration for " + project.getId() + " with attributes: " + childrenRewrite.keySet());
                        project.getBuild().getPlugins().add(newPlugin);
                    }
                });
            }
        } else {
            final var parentOverrides = overrides.getChild("parent");
            if (parentOverrides != null) {
                logger.debug("Rewriting " + plugin + " configuration in " + pom.getId() + " with: " + parentOverrides);
                plugin.setConfiguration(Xpp3Dom.mergeXpp3Dom(xpp3, parentOverrides));
            }

            final var childOverrides = overrides.getChild("child");
            if (childOverrides != null) {
                children.forEach((project, plugins) -> {
                    final var childDefinition = plugins.get(plugin.getKey());
                    if (Xpp3Dom.class.isInstance(childDefinition.getConfiguration())) { // just rewrite since it exists
                        logger.debug("Rewriting configuration for " + plugin + " in " + project.getId() + " with: " + childOverrides);
                        childDefinition.setConfiguration(Xpp3Dom.mergeXpp3Dom(Xpp3Dom.class.cast(childDefinition.getConfiguration()), parentOverrides));
                    } else {
                        final var newPlugin = new Plugin();
                        newPlugin.setGroupId(plugin.getGroupId());
                        newPlugin.setArtifactId(plugin.getArtifactId());
                        newPlugin.setVersion(plugin.getVersion());
                        newPlugin.setConfiguration(childOverrides);
                        logger.debug("Adding " + plugin + " configuration for " + project.getId() + " with: " + childOverrides);
                        project.getBuild().getPlugins().add(newPlugin);
                    }
                });
            }
        }
    }

    private Xpp3Dom getOverrides(final Xpp3Dom xpp3) {
        for (int i = 0; i < xpp3.getChildCount(); i++) {
            final var child = xpp3.getChild(i);
            if (!attributeName.equals(child.getName())) {
                continue;
            }
            xpp3.removeChild(i);
            return child;
        }
        return null;
    }

    private Map<String, String> extractAttributes(final String prefix, final String overrides) {
        if (overrides == null) {
            return null;
        }

        final int start = overrides.indexOf(prefix + '(');
        if (start < 0) {
            return null;
        }

        final int end = overrides.indexOf(')', start);
        if (end < 0) {
            return null;
        }

        return Stream.of(overrides.substring(start + prefix.length() + 1, end).split(";"))
                .map(it -> {
                    final var sep = it.indexOf('=');
                    return sep > 0 ? new String[]{it.substring(0, sep), it.substring(sep + 1)} : new String[]{it, null};
                })
                .collect(toMap(it -> it[0], it -> it[1], (a, b) -> a, LinkedHashMap::new));
    }
}
