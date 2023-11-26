/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pku;

import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.Pointer;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.util.collection.Maps;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;

/**
 * Represents work list in pointer analysis.
 */
/*final class WorkList {

    private final Map<Pointer, PointsToSet> pointerEntries = Maps.newLinkedHashMap();

    private final Queue<Edge<CSCallSite, CSMethod>> callEdges = new ArrayDeque<>();

    void addEntry(Pointer pointer, PointsToSet pointsToSet) {
        PointsToSet set = pointerEntries.get(pointer);
        if (set != null) {
            set.addAll(pointsToSet);
        } else {
            PointsToSet debug = pointsToSet.copy();
            pointerEntries.put(pointer, pointsToSet.copy());
        }
    }

    void addEntry(Edge<CSCallSite, CSMethod> edge) {
        callEdges.add(edge);
    }

    Entry pollEntry() {
        if (!callEdges.isEmpty()) {
            // for correctness, we need to ensure that any call edges in
            // the work list must be processed prior to the pointer entries
            return new CallEdgeEntry(callEdges.poll());
        } else if (!pointerEntries.isEmpty()) {
            var it = pointerEntries.entrySet().iterator();
            var e = it.next();
            it.remove();
            return new PointerEntry(e.getKey(), e.getValue());
        } else {
            throw new NoSuchElementException();
        }
    }

    boolean isEmpty() {
        return pointerEntries.isEmpty() && callEdges.isEmpty();
    }

    interface Entry {
    }

    // record Entry(Pointer pointer, PointsToSet pointsToSet) {
    // }
    record PointerEntry(Pointer pointer, PointsToSet  pointsToSet)
            implements Entry {
    }

    record CallEdgeEntry(Edge<CSCallSite, CSMethod> edge)
            implements Entry {
    }
}*/

class WorkList {

    private final Queue<Entry> entries = new ArrayDeque<>();

    /**
     * Adds an entry to the work list.
     */
    void addEntry(Pointer pointer, PointsToSet pointsToSet) {
        entries.add(new Entry(pointer, pointsToSet));
    }

    /**
     * Retrieves and removes an entry from this queue, or returns null
     * if this work list is empty.
     */
    Entry pollEntry() {
        return entries.poll();
    }

    /**
     * @return true if the work list is empty, otherwise false.
     */
    boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Represents entries in the work list.
     * Each entry consists of a pointer and a points-to set.
     */
    record Entry(Pointer pointer, PointsToSet pointsToSet) {
    }
}
