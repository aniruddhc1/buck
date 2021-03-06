/*
 * Copyright 2012-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.graph;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Sets;

public class DefaultImmutableDirectedAcyclicGraph<T> implements ImmutableDirectedAcyclicGraph<T> {

  private final ImmutableSet<T> nodes;
  private final ImmutableSetMultimap<T, T> outgoingEdges;
  private final ImmutableSetMultimap<T, T> incomingEdges;

  public DefaultImmutableDirectedAcyclicGraph(MutableDirectedGraph<T> graph) {
    Preconditions.checkNotNull(graph);
    Preconditions.checkArgument(graph.isAcyclic());
    this.nodes = graph.createImmutableCopyOfNodes();
    this.outgoingEdges = graph.createImmutableCopyOfOutgoingEdges();
    this.incomingEdges = graph.createImmutableCopyOfIncomingEdges();
  }

  @Override
  public ImmutableSet<T> getOutgoingNodesFor(T source) {
    return outgoingEdges.get(source);
  }

  @Override
  public ImmutableSet<T> getIncomingNodesFor(T sink) {
    return incomingEdges.get(sink);
  }

  @Override
  public ImmutableSet<T> getNodesWithNoOutgoingEdges() {
    return ImmutableSet.copyOf(Sets.difference(nodes, outgoingEdges.keySet()));
  }

  @Override
  public ImmutableSet<T> getNodesWithNoIncomingEdges() {
    return ImmutableSet.copyOf(Sets.difference(nodes, incomingEdges.keySet()));
  }

  public String toDebugString() {
    StringBuilder builder = new StringBuilder();
    builder.append("Number of nodes: " + nodes.size() + "\n");
    for (T node : nodes) {
      for (T sink : getOutgoingNodesFor(node)) {
        builder.append(String.format("%s => %s\n", node, sink));
      }
    }
    return builder.toString();
  }

  /** @return the number of nodes in the graph */
  public int getNodeCount() {
    return nodes.size();
  }

  /** @return an unmodifiable view of the nodes in this graph */
  public Iterable<T> getNodes() {
    return nodes;
  }
}
