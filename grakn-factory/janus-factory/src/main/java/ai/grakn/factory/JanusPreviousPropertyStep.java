/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package ai.grakn.factory;

import org.apache.tinkerpop.gremlin.process.traversal.Pop;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.Scoping;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.FlatMapStep;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.TraverserRequirement;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.janusgraph.core.JanusGraphTransaction;
import org.janusgraph.core.JanusGraphVertex;
import org.janusgraph.graphdb.tinkerpop.optimize.JanusGraphTraversalUtil;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import static java.util.Collections.emptyIterator;

/**
 * Optimise a particular traversal in Janus:
 * <p>
 * <code>
 * g.V().outE().values(c).as(b).V().filter(__.properties(a).where(P.eq(b)));
 * </code>
 * <p>
 * This step can be used in place of {@code V().filter(..)} since we are referring to a previously visited property in
 * the traversal.
 *
 * @author Felix Chapman
 */
class JanusPreviousPropertyStep<S> extends FlatMapStep<S, JanusGraphVertex> implements Scoping {

    private static final long serialVersionUID = -8906462828437711078L;
    private final String propertyKey;
    private final String stepLabel;

    /**
     * @param traversal the traversal that contains this step
     * @param propertyKey the property key that we are looking up
     * @param stepLabel
     * the step label that refers to a previously visited value in the traversal.
     * e.g. in {@code g.V().as(b)}, {@code b} is a step label.
     */
    JanusPreviousPropertyStep(Traversal.Admin traversal, String propertyKey, String stepLabel) {
        super(traversal);
        this.propertyKey = Objects.requireNonNull(propertyKey);
        this.stepLabel = Objects.requireNonNull(stepLabel);
    }

    @Override
    protected Iterator<JanusGraphVertex> flatMap(Traverser.Admin<S> traverser) {
        JanusGraphTransaction tx = JanusGraphTraversalUtil.getTx(this.traversal);

        // Retrieve property value to look-up, that is identified in the traversal by the `stepLabel`
        Object value = getNullableScopeValue(Pop.first, stepLabel, traverser);

        return value != null ? verticesWithProperty(tx, value) : emptyIterator();
    }

    /**
     * Look up vertices in Janus which have a property {@link JanusPreviousPropertyStep#propertyKey} with the given
     * value.
     * @param tx the Janus transaction to read from
     * @param value the value that the property should have
     */
    private Iterator<JanusGraphVertex> verticesWithProperty(JanusGraphTransaction tx, Object value) {
        return tx.query().has(propertyKey, value).vertices().iterator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        JanusPreviousPropertyStep<?> that = (JanusPreviousPropertyStep<?>) o;

        return propertyKey.equals(that.propertyKey) && stepLabel.equals(that.stepLabel);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + propertyKey.hashCode();
        result = 31 * result + stepLabel.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return StringFactory.stepString(this, propertyKey, stepLabel);
    }

    @Override
    public Set<String> getScopeKeys() {
        return Collections.singleton(stepLabel);
    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        // This step requires being able to access previously visited properties in the traversal,
        // so it needs `LABELED_PATH`.
        return EnumSet.of(TraverserRequirement.LABELED_PATH);
    }
}
