/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 * 
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * 
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.tools.visualvm.heapviewer.truffle.r;

import com.sun.tools.visualvm.heapviewer.HeapContext;
import com.sun.tools.visualvm.heapviewer.model.HeapViewerNode;
import com.sun.tools.visualvm.heapviewer.truffle.AbstractObjectsProvider;
import com.sun.tools.visualvm.heapviewer.truffle.TruffleLanguageHeapFragment;
import com.sun.tools.visualvm.heapviewer.truffle.TruffleObject;
import com.sun.tools.visualvm.heapviewer.truffle.TruffleSummaryView;
import com.sun.tools.visualvm.heapviewer.truffle.TruffleType;
import com.sun.tools.visualvm.heapviewer.ui.HeapView;
import com.sun.tools.visualvm.heapviewer.ui.HeapViewerActions;
import com.sun.tools.visualvm.heapviewer.ui.HeapViewerFeature;
import com.sun.tools.visualvm.heapviewer.ui.HeapViewerNodeAction;
import com.sun.tools.visualvm.heapviewer.ui.SummaryView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.swing.Icon;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.ui.swing.renderer.ProfilerRenderer;
import org.netbeans.modules.profiler.heapwalk.ui.icons.HeapWalkerIcons;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Jiri Sedlacek
 */
class RHeapSummary {
    
    private static final String VIEW_ID = "r_summary"; // NOI18N
    
    
    @ServiceProvider(service=HeapViewerFeature.Provider.class)
    public static class RSummaryProvider extends HeapViewerFeature.Provider {

        public HeapViewerFeature getFeature(HeapContext context, HeapViewerActions actions) {
            if (RHeapFragment.isRHeap(context)) {
                Icon icon = RSupport.createBadgedIcon(HeapWalkerIcons.PROPERTIES);
                return new TruffleSummaryView(VIEW_ID, icon, context, actions);
            }

            return null;
        }

    }
    
    
    @ServiceProvider(service=SummaryView.ContentProvider.class, position = 100)
    public static class RSummaryOverviewProvider extends SummaryView.ContentProvider {

        @Override
        public HeapView createSummary(String viewID, HeapContext context, HeapViewerActions actions, Collection<HeapViewerNodeAction.Provider> actionProviders) {
            if (RHeapFragment.isRHeap(context))
                return new RSummaryOverview(context);
            
            return null;
        }
        
    }
    
    private static class RSummaryOverview extends TruffleSummaryView.OverviewSection {
        
        RSummaryOverview(HeapContext context) {
            super(context);
        }

    }
    
    
    @ServiceProvider(service=SummaryView.ContentProvider.class, position = 300)
    public static class RSummaryObjectsProvider extends SummaryView.ContentProvider {

        @Override
        public HeapView createSummary(String viewID, HeapContext context, HeapViewerActions actions, Collection<HeapViewerNodeAction.Provider> actionProviders) {
            if (RHeapFragment.isRHeap(context))
                return new RSummaryObjects(context, actions, actionProviders);
            
            return null;
        }
        
    }
    
    private static class RSummaryObjects extends TruffleSummaryView.ObjectsSection {
        
        RSummaryObjects(HeapContext context, HeapViewerActions actions, Collection<HeapViewerNodeAction.Provider> actionProviders) {
            super(context, actions, actionProviders);
        }

        
        @Override
        protected Runnable typesByCountDisplayer(HeapViewerActions actions) {
            return new Runnable() {
                public void run() {
                    RObjectsView objectsView = actions.findFeature(RObjectsView.class);
                    if (objectsView != null) {
                        objectsView.configureTypesByObjectsCount();
                        actions.selectFeature(objectsView);
                    }
                }
            };
        }

        @Override
        protected Runnable typesBySizeDisplayer(HeapViewerActions actions) {
            return new Runnable() {
                public void run() {
                    RObjectsView objectsView = actions.findFeature(RObjectsView.class);
                    if (objectsView != null) {
                        objectsView.configureTypesByObjectsSize();
                        actions.selectFeature(objectsView);
                    }
                }
            };
        }

        @Override
        protected Runnable objectsBySizeDisplayer(HeapViewerActions actions) {
            return new Runnable() {
                public void run() {
                    RObjectsView objectsView = actions.findFeature(RObjectsView.class);
                    if (objectsView != null) {
                        objectsView.configureObjectsBySize();
                        actions.selectFeature(objectsView);
                    }
                }
            };
        }

        @Override
        protected Runnable dominatorsByRetainedSizeDisplayer(HeapViewerActions actions) {
            return new Runnable() {
                public void run() {
                    RObjectsView objectsView = actions.findFeature(RObjectsView.class);
                    if (objectsView != null) {
                        objectsView.configureDominatorsByRetainedSize();
                        actions.selectFeature(objectsView);
                    }
                }
            };
        }
        
        
        @Override
        protected List<RObject> dominators(TruffleLanguageHeapFragment heapFragment) {
            int maxSearchInstances = 10000;

            List<Instance> searchInstances = heapFragment.getHeap().getBiggestObjectsByRetainedSize(maxSearchInstances);
            Iterator<Instance> searchInstancesI = searchInstances.iterator();
            while (searchInstancesI.hasNext()) {
                Instance instance = searchInstancesI.next();
                if (!RObject.isRObject(instance))
                    searchInstancesI.remove();
            }
            
            Set<Instance> rootInstances = AbstractObjectsProvider.getDominatorRoots(searchInstances);
            List<RObject> rootObjects = new ArrayList();
            for (Instance root : rootInstances) rootObjects.add(new RObject(root));
            
            return rootObjects;
        }
        
        
        @Override
        protected HeapViewerNode typeNode(TruffleType type, Heap heap) {
            return new RTypeNode((RType)type);
        }

        @Override
        protected ProfilerRenderer typeRenderer(Heap heap) {
            return new RObjectsContainer.Renderer();
        }
        
        @Override
        protected HeapViewerNode objectNode(TruffleObject object, Heap heap) {
            return new RObjectNode((RObject)object, object.getType(heap));
        }

        @Override
        protected ProfilerRenderer objectRenderer(Heap heap) {
            return new RObjectNode.Renderer(heap);
        }

    }
    
}
