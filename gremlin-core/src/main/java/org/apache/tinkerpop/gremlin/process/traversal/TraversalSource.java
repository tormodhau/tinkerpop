/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.process.traversal;

import org.apache.tinkerpop.gremlin.process.computer.Computer;
import org.apache.tinkerpop.gremlin.process.computer.GraphComputer;
import org.apache.tinkerpop.gremlin.process.computer.traversal.strategy.decoration.VertexProgramStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.creation.TranslationStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.SackStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.SideEffectStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.util.Translator;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.util.function.ConstantSupplier;

import java.io.Serializable;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A {@code TraversalSource} is used to create {@link Traversal} instances.
 * A traversal source can generate any number of {@link Traversal} instances.
 * A traversal source is primarily composed of a {@link Graph} and a {@link TraversalStrategies}.
 * Various {@code withXXX}-based methods are used to configure the traversal strategies (called "configurations").
 * Various other methods (dependent on the traversal source type) will then generate a traversal given the graph and configured strategies (called "spawns").
 * A traversal source is immutable in that fluent chaining of configurations create new traversal sources.
 * This is unlike {@link Traversal} and {@link GraphComputer}, where chained methods configure the same instance.
 * Every traversal source implementation must maintain two constructors to enable proper reflection-based construction.
 * <p/>
 * {@code TraversalSource(Graph)} and {@code TraversalSource(Graph,TraversalStrategies)}
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface TraversalSource extends Cloneable {

    public static final String SET_ANONYMOUS_TRAVERSAL_FUNCTION = "setAnonymousTraversalFunction";

    /**
     * Get the {@link TraversalStrategies} associated with this traversal source.
     *
     * @return the traversal strategies of the traversal source
     */
    public TraversalStrategies getStrategies();

    /**
     * Get the {@link Graph) associated with this traversal source.
     *
     * @return the graph of the traversal source
     */
    public Graph getGraph();

    /////////////////////////////

    /**
     * Add an arbitrary collection of {@link TraversalStrategy} instances to the traversal source.
     *
     * @param traversalStrategies a colleciton of traversal strategies to add
     * @return a new traversal source with updated strategies
     */
    public default TraversalSource withStrategies(final TraversalStrategy... traversalStrategies) {
        final TraversalSource clone = this.clone();
        clone.getStrategies().addStrategies(traversalStrategies);
        return clone;
    }

    /**
     * Remove an arbitrary collection of {@link TraversalStrategy} classes from the traversal source.
     *
     * @param traversalStrategyClasses a collection of traversal strategy classes to remove
     * @return a new traversal source with updated strategies
     */
    @SuppressWarnings({"unchecked", "varargs"})
    public default TraversalSource withoutStrategies(final Class<? extends TraversalStrategy>... traversalStrategyClasses) {
        final TraversalSource clone = this.clone();
        clone.getStrategies().removeStrategies(traversalStrategyClasses);
        return clone;
    }

    /**
     * Add a {@link Function} that will generate a {@link GraphComputer} from the {@link Graph} that will be used to execute the traversal.
     * This adds a {@link VertexProgramStrategy} to the strategies.
     *
     * @param computer a builder to generate a graph computer from the graph
     * @return a new traversal source with updated strategies
     */
    public default TraversalSource withComputer(final Computer computer) {
        Class<? extends GraphComputer> graphComputerClass;
        try {
            graphComputerClass = computer.apply(this.getGraph()).getClass();
        } catch (final Exception e) {
            graphComputerClass = computer.getGraphComputerClass();
        }
        final List<TraversalStrategy<?>> graphComputerStrategies = TraversalStrategies.GlobalCache.getStrategies(graphComputerClass).toList();
        final TraversalStrategy[] traversalStrategies = new TraversalStrategy[graphComputerStrategies.size() + 1];
        traversalStrategies[0] = new VertexProgramStrategy(computer);
        for (int i = 0; i < graphComputerStrategies.size(); i++) {
            traversalStrategies[i + 1] = graphComputerStrategies.get(i);
        }
        final TraversalSource clone = this.withStrategies(traversalStrategies);
        TraversalHelper.addSourceToCreationStrategies(clone, computer);
        return clone;
    }

    /**
     * Add a {@link Translator} to translate any traversal from this traversal source language into some target language.
     *
     * @param translator the translator that will do the source to targer language translation
     * @return a new traversal source with updated strategies
     */
    public default TraversalSource withTranslator(final Translator translator) {
        return this.withStrategies(new TranslationStrategy(translator, this.clone(), null));
    }

    /**
     * Add a {@link GraphComputer} class used to execute the traversal.
     * This adds a {@link VertexProgramStrategy} to the strategies.
     *
     * @param graphComputerClass the graph computer class
     * @return a new traversal source with updated strategies
     */
    public default TraversalSource withComputer(final Class<? extends GraphComputer> graphComputerClass) {
        return this.withComputer(Computer.compute(graphComputerClass));
    }

    /**
     * Add the standard {@link GraphComputer} of the graph that will be used to execute the traversal.
     * This adds a {@link VertexProgramStrategy} to the strategies.
     *
     * @return a new traversal source with updated strategies
     */
    public default TraversalSource withComputer() {
        return this.withComputer(Computer.compute());
    }

    /**
     * Add a sideEffect to be used throughout the life of a spawned {@link Traversal}.
     * This adds a {@link org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.SideEffectStrategy} to the strategies.
     *
     * @param key          the key of the sideEffect
     * @param initialValue a supplier that produces the initial value of the sideEffect
     * @param reducer      a reducer to merge sideEffect mutations into a single result
     * @return a new traversal source with updated strategies
     */
    public default <A> TraversalSource withSideEffect(final String key, final Supplier<A> initialValue, final BinaryOperator<A> reducer) {
        final TraversalSource clone = this.clone();
        TraversalHelper.addSourceToCreationStrategies(clone, key, initialValue, reducer);
        SideEffectStrategy.addSideEffect(clone.getStrategies(), key, (A) initialValue, reducer);
        return clone;
    }

    /**
     * Add a sideEffect to be used throughout the life of a spawned {@link Traversal}.
     * This adds a {@link org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.SideEffectStrategy} to the strategies.
     *
     * @param key          the key of the sideEffect
     * @param initialValue the initial value of the sideEffect
     * @param reducer      a reducer to merge sideEffect mutations into a single result
     * @return a new traversal source with updated strategies
     */
    public default <A> TraversalSource withSideEffect(final String key, final A initialValue, final BinaryOperator<A> reducer) {
        final TraversalSource clone = this.clone();
        TraversalHelper.addSourceToCreationStrategies(clone, key, initialValue, reducer);
        SideEffectStrategy.addSideEffect(clone.getStrategies(), key, initialValue, reducer);
        return clone;
    }

    /**
     * Add a sideEffect to be used throughout the life of a spawned {@link Traversal}.
     * This adds a {@link org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.SideEffectStrategy} to the strategies.
     *
     * @param key          the key of the sideEffect
     * @param initialValue a supplier that produces the initial value of the sideEffect
     * @return a new traversal source with updated strategies
     */
    public default <A> TraversalSource withSideEffect(final String key, final Supplier<A> initialValue) {
        final TraversalSource clone = this.clone();
        TraversalHelper.addSourceToCreationStrategies(clone, key, initialValue);
        SideEffectStrategy.addSideEffect(clone.getStrategies(), key, initialValue, null);
        return clone;
    }

    /**
     * Add a sideEffect to be used throughout the life of a spawned {@link Traversal}.
     * This adds a {@link org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.SideEffectStrategy} to the strategies.
     *
     * @param key          the key of the sideEffect
     * @param initialValue the initial value of the sideEffect
     * @return a new traversal source with updated strategies
     */
    public default <A> TraversalSource withSideEffect(final String key, final A initialValue) {
        final TraversalSource clone = this.clone();
        TraversalHelper.addSourceToCreationStrategies(clone, key, initialValue);
        SideEffectStrategy.addSideEffect(clone.getStrategies(), key, initialValue, null);
        return clone;
    }

    /**
     * Add a sack to be used throughout the life of a spawned {@link Traversal}.
     * This adds a {@link org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.SackStrategy} to the strategies.
     *
     * @param initialValue  a supplier that produces the initial value of the sideEffect
     * @param splitOperator the sack split operator
     * @param mergeOperator the sack merge operator
     * @return a new traversal source with updated strategies
     */
    public default <A> TraversalSource withSack(final Supplier<A> initialValue, final UnaryOperator<A> splitOperator, final BinaryOperator<A> mergeOperator) {
        final TraversalSource clone = this.withStrategies(SackStrategy.<A>build().initialValue(initialValue).splitOperator(splitOperator).mergeOperator(mergeOperator).create());
        TraversalHelper.addSourceToCreationStrategies(clone, initialValue, splitOperator, mergeOperator);
        return clone;
    }

    /**
     * Add a sack to be used throughout the life of a spawned {@link Traversal}.
     * This adds a {@link org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.SackStrategy} to the strategies.
     *
     * @param initialValue  the initial value of the sideEffect
     * @param splitOperator the sack split operator
     * @param mergeOperator the sack merge operator
     * @return a new traversal source with updated strategies
     */
    public default <A> TraversalSource withSack(final A initialValue, final UnaryOperator<A> splitOperator, final BinaryOperator<A> mergeOperator) {
        final TraversalSource clone = this.withStrategies(SackStrategy.<A>build().initialValue(new ConstantSupplier<>(initialValue)).splitOperator(splitOperator).mergeOperator(mergeOperator).create());
        TraversalHelper.addSourceToCreationStrategies(clone, initialValue, splitOperator, mergeOperator);
        return clone;
    }

    /**
     * Add a sack to be used throughout the life of a spawned {@link Traversal}.
     * This adds a {@link org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.SackStrategy} to the strategies.
     *
     * @param initialValue the initial value of the sideEffect
     * @return a new traversal source with updated strategies
     */
    public default <A> TraversalSource withSack(final A initialValue) {
        final TraversalSource clone = this.withStrategies(SackStrategy.<A>build().initialValue(new ConstantSupplier<>(initialValue)).create());
        TraversalHelper.addSourceToCreationStrategies(clone, initialValue);
        return clone;
    }

    /**
     * Add a sack to be used throughout the life of a spawned {@link Traversal}.
     * This adds a {@link org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.SackStrategy} to the strategies.
     *
     * @param initialValue a supplier that produces the initial value of the sideEffect
     * @return a new traversal source with updated strategies
     */
    public default <A> TraversalSource withSack(final Supplier<A> initialValue) {
        final TraversalSource clone = this.withStrategies(SackStrategy.<A>build().initialValue(initialValue).create());
        TraversalHelper.addSourceToCreationStrategies(clone, initialValue);
        return clone;
    }

    /**
     * Add a sack to be used throughout the life of a spawned {@link Traversal}.
     * This adds a {@link org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.SackStrategy} to the strategies.
     *
     * @param initialValue  a supplier that produces the initial value of the sideEffect
     * @param splitOperator the sack split operator
     * @return a new traversal source with updated strategies
     */
    public default <A> TraversalSource withSack(final Supplier<A> initialValue, final UnaryOperator<A> splitOperator) {
        final TraversalSource clone = this.withStrategies(SackStrategy.<A>build().initialValue(initialValue).splitOperator(splitOperator).create());
        TraversalHelper.addSourceToCreationStrategies(clone, initialValue, splitOperator);
        return clone;
    }

    /**
     * Add a sack to be used throughout the life of a spawned {@link Traversal}.
     * This adds a {@link org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.SackStrategy} to the strategies.
     *
     * @param initialValue  the initial value of the sideEffect
     * @param splitOperator the sack split operator
     * @return a new traversal source with updated strategies
     */
    public default <A> TraversalSource withSack(final A initialValue, final UnaryOperator<A> splitOperator) {
        final TraversalSource clone = this.withStrategies(SackStrategy.<A>build().initialValue(new ConstantSupplier<>(initialValue)).splitOperator(splitOperator).create());
        TraversalHelper.addSourceToCreationStrategies(clone, initialValue, splitOperator);
        return clone;
    }

    /**
     * Add a sack to be used throughout the life of a spawned {@link Traversal}.
     * This adds a {@link org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.SackStrategy} to the strategies.
     *
     * @param initialValue  a supplier that produces the initial value of the sideEffect
     * @param mergeOperator the sack merge operator
     * @return a new traversal source with updated strategies
     */
    public default <A> TraversalSource withSack(final Supplier<A> initialValue, final BinaryOperator<A> mergeOperator) {
        final TraversalSource clone = this.withStrategies(SackStrategy.<A>build().initialValue(initialValue).mergeOperator(mergeOperator).create());
        TraversalHelper.addSourceToCreationStrategies(clone, initialValue, mergeOperator);
        return clone;
    }

    /**
     * Add a sack to be used throughout the life of a spawned {@link Traversal}.
     * This adds a {@link org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.SackStrategy} to the strategies.
     *
     * @param initialValue  the initial value of the sideEffect
     * @param mergeOperator the sack merge operator
     * @return a new traversal source with updated strategies
     */
    public default <A> TraversalSource withSack(final A initialValue, final BinaryOperator<A> mergeOperator) {
        final TraversalSource clone = this.withStrategies(SackStrategy.<A>build().initialValue(new ConstantSupplier<>(initialValue)).mergeOperator(mergeOperator).create());
        TraversalHelper.addSourceToCreationStrategies(clone, initialValue, mergeOperator);
        return clone;
    }

    /**
     * The clone-method should be used to create immutable traversal sources with each call to a configuration method.
     * The clone-method should clone the internal {@link TraversalStrategies}, mutate the cloned strategies accordingly,
     * and then return the cloned traversal source.
     *
     * @return the cloned traversal source
     */
    @SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
    public TraversalSource clone();

    /**
     * @deprecated As of release 3.2.0. Please use {@link Graph#traversal(Class)}.
     */
    @Deprecated
    public interface Builder<C extends TraversalSource> extends Serializable {

        /**
         * @deprecated As of release 3.2.0. Please use {@link Graph#traversal(Class)}.
         */
        @Deprecated
        public Builder engine(final TraversalEngine.Builder engine);

        /**
         * @deprecated As of release 3.2.0. Please use {@link Graph#traversal(Class)}.
         */
        @Deprecated
        public Builder with(final TraversalStrategy strategy);

        /**
         * @deprecated As of release 3.2.0. Please use {@link Graph#traversal(Class)}.
         */
        @Deprecated
        public Builder without(final Class<? extends TraversalStrategy> strategyClass);

        /**
         * @deprecated As of release 3.2.0. Please use {@link Graph#traversal(Class)}.
         */
        @Deprecated
        public C create(final Graph graph);
    }

}
