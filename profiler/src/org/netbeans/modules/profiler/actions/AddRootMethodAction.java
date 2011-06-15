/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */

package org.netbeans.modules.profiler.actions;

import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.lib.profiler.common.ProfilingSettings;
import org.netbeans.modules.profiler.ui.NBSwingWorker;
import org.netbeans.modules.profiler.api.ProfilingSettingsManager;
import org.netbeans.modules.profiler.utils.IDEUtils;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.NodeAction;
import java.util.ArrayList;
import java.util.List;
import org.netbeans.modules.profiler.api.EditorSupport;
import org.netbeans.modules.profiler.api.java.JavaProfilerSource;
import org.netbeans.modules.profiler.api.java.JavaProfilerSource.MethodInfo;
import org.netbeans.modules.profiler.api.ProfilerDialogs;


/**
 * Action enabled on Java methods, constructors and static initializers that will add the particular
 * method/constructor/static initializer as a root method for Profiling of Part of Application.
 *
 * @author Ian Formanek
 * @author Misha Dmitriev
 * @author Jiri Sedlacek
 */
public final class AddRootMethodAction extends NodeAction {
    //~ Constructors -------------------------------------------------------------------------------------------------------------

    public AddRootMethodAction() {
        putValue("noIconInMenu", Boolean.TRUE); //NOI18N
    }

    //~ Methods ------------------------------------------------------------------------------------------------------------------

    /**
     * @return The context sensitive help
     */
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    /**
     * @return The name of the action
     */
    public String getName() {
        return NbBundle.getMessage(AddRootMethodAction.class, "LBL_AddRootMethodAction"); // NOI18N
    }

    /**
     * @return false, as it runs in the event queue
     */
    protected boolean asynchronous() {
        return false;
    } // runs in event thread

    /**
     * @param nodes current activated nodes
     * @return true
     */
    protected boolean enable(final Node[] nodes) {
        return true;
    }

    protected void performAction(final Node[] nodes) {
        new NBSwingWorker() {
                protected void doInBackground() {
                    try {
                        // Get DataObject
                        DataObject dobj = (DataObject) nodes[0].getLookup().lookup(DataObject.class);
                        
                        if (dobj == null) {
                            return;
                        }
                        
                        JavaProfilerSource src = JavaProfilerSource.createFrom(dobj.getPrimaryFile());

                        if (src == null) {
                            return;
                        }
                        
                        // Read current offset in editor
                        int currentOffsetInEditor = EditorSupport.getDefault().getCurrentOffset();

                        if (currentOffsetInEditor == -1) {
                            return;
                        }

                        // Get method at cursor
                        MethodInfo resolvedMethod = src.resolveMethodAtPosition(currentOffsetInEditor);

                        if (resolvedMethod == null) {
                            ProfilerDialogs.displayWarning(NbBundle.getMessage(AddRootMethodAction.class,
                                                                                "MSG_NoMethodFoundAtPosition")); // NOI18N

                            return;
                        }

                        if (resolvedMethod == null) {
                            return;
                        }

                        // Check if method is executable
                        if (!resolvedMethod.isExecutable()) {
                            ProfilerDialogs.displayInfo(NbBundle.getMessage(AddRootMethodAction.class,
                                                        "MSG_CannotAddAbstractNativeProfilingRoot")); // NOI18N

                            return;
                        }

                        // Resolve owner project
                        Project project = FileOwnerQuery.getOwner(dobj.getPrimaryFile());

                        // Specify Profiling Settings as a context
                        ProfilingSettings[] projectSettings = ProfilingSettingsManager.getDefault().getProfilingSettings(project)
                                                                                      .getProfilingSettings();
                        List<ProfilingSettings> cpuSettings = new ArrayList();

                        for (ProfilingSettings settings : projectSettings) {
                            if (ProfilingSettings.isCPUSettings(settings.getProfilingType())) {
                                cpuSettings.add(settings);
                            }
                        }

                        ProfilingSettings settings = IDEUtils.selectSettings(ProfilingSettings.PROFILE_CPU_PART,
                                                                             cpuSettings.toArray(new ProfilingSettings[cpuSettings
                                                                                                                       .size()]),
                                                                             null);

                        if (settings == null) {
                            return; // cancelled by the user
                        }

                        settings.addRootMethod(resolvedMethod.getClassName(), resolvedMethod.getVMName(),
                                               resolvedMethod.getSignature());

                        if (cpuSettings.contains(settings)) {
                            ProfilingSettingsManager.getDefault().storeProfilingSettings(projectSettings, settings, project);
                        } else {
                            ProfilingSettings[] newProjectSettings = new ProfilingSettings[projectSettings.length + 1];
                            System.arraycopy(projectSettings, 0, newProjectSettings, 0, projectSettings.length);
                            newProjectSettings[projectSettings.length] = settings;
                            ProfilingSettingsManager.getDefault().storeProfilingSettings(newProjectSettings, settings, project);
                        }
                    } catch (Exception ex) {
                        ProfilerDialogs.displayWarning(NbBundle.getMessage(AddRootMethodAction.class,
                                                        "MSG_ProblemAddingRootMethod")); // NOI18N
                    }
                }
            }.execute();
    }
}
